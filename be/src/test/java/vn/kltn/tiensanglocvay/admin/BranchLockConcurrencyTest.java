package vn.kltn.tiensanglocvay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.kltn.tiensanglocvay.admin.AdminTestHelper.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B7 tồn đọng: POST /applications và POST /admin/branches/{id}/deactivate cùng khóa dòng branch (SELECT … FOR UPDATE), nên hồ sơ mới
 * không vào chi nhánh đang được ngừng hoạt động và không lọt khỏi danh sách hồ sơ được chuyển.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class BranchLockConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    AdminTestHelper h;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        h = new AdminTestHelper(mvc, json);
        pool = Executors.newFixedThreadPool(12);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private int newBranch(String admin, String code) throws Exception {
        return read(h.send(post("/admin/branches"), Map.of("code", code, "name", "Chi nhánh " + code, "address", "Cần Thơ", "phone", "0292000000"),
                admin).andExpect(status().isCreated()), "$.id");
    }

    private static String text(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Giao dịch khác đang ngừng chi nhánh (đã đổi INACTIVE nhưng chưa commit, giữ khóa dòng): tạo hồ sơ phải chờ khóa, rồi thấy chi nhánh
     * INACTIVE và từ chối. Nếu chỉ đọc không khóa, yêu cầu sẽ thấy ACTIVE cũ và hồ sơ lọt vào chi nhánh đã ngừng.
     */
    @Test
    void createApplication_waitsForBranchLockAndRechecksActive() throws Exception {
        String admin = h.admin();
        int branch = newBranch(admin, "KH01");
        String customer = h.newCustomer();
        String plan = h.confirmedPlan(customer);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(s -> {
            jdbc.update("update branch set status = 'INACTIVE' where id = ?", branch);
            locked.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<MockHttpServletResponse> create = pool.submit(() -> h.createApp(customer, plan, branch).andReturn().getResponse());
        Thread.sleep(800);
        assertThat(create.isDone()).as("tạo hồ sơ phải chờ khóa dòng chi nhánh").isFalse();

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        MockHttpServletResponse res = create.get(20, TimeUnit.SECONDS);
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(text(res), "$.message")).isEqualTo("Chi nhánh không hợp lệ.");
        assertThat(jdbc.queryForObject("select count(*) from loan_application where branch_id = ?", Integer.class, branch)).isZero();
    }

    /** Ngược lại: nhiều yêu cầu tạo hồ sơ chạy cùng lúc với ngừng chi nhánh; hồ sơ nào được tạo đều phải đã được chuyển sang chi nhánh nhận. */
    @Test
    void createApplicationsRacingDeactivate_noApplicationLeftInClosedBranch() throws Exception {
        String admin = h.admin();
        int branch = newBranch(admin, "KH02");
        int n = 8;
        List<String> tokens = new ArrayList<>();
        List<String> plans = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String t = h.newCustomer();
            tokens.add(t);
            plans.add(h.confirmedPlan(t));
        }

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> creates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String t = tokens.get(i);
            String p = plans.get(i);
            creates.add(pool.submit(() -> {
                gate.await();
                return h.createApp(t, p, branch).andReturn().getResponse();
            }));
        }
        Future<MockHttpServletResponse> deactivate = pool.submit(() -> {
            gate.await();
            return h.send(post("/admin/branches/" + branch + "/deactivate"), Map.of("transferToBranchId", 1), admin).andReturn().getResponse();
        });
        gate.countDown();

        List<String> created = new ArrayList<>();
        for (Future<MockHttpServletResponse> f : creates) {
            MockHttpServletResponse r = f.get(60, TimeUnit.SECONDS);
            assertThat(r.getStatus()).as(text(r)).isIn(201, 400);
            if (r.getStatus() == 201) {
                created.add(JsonPath.read(text(r), "$.id"));
            }
        }
        MockHttpServletResponse d = deactivate.get(60, TimeUnit.SECONDS);
        assertThat(d.getStatus()).as(text(d)).isEqualTo(200);

        assertThat(jdbc.queryForObject("select status from branch where id = ?", String.class, branch)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from loan_application where branch_id = ?", Integer.class, branch))
                .as("không còn hồ sơ nào ở chi nhánh đã ngừng").isZero();
        for (String id : created) {
            assertThat(jdbc.queryForObject("select branch_id from loan_application where public_id = ?::uuid", Integer.class, id)).isEqualTo(1);
        }
        int transferred = JsonPath.read(text(d), "$.transferred");
        assertThat(transferred).isLessThanOrEqualTo(created.size());
        // Sau khi ngừng, tạo hồ sơ vào chi nhánh đó bị từ chối
        String late = h.newCustomer();
        h.createApp(late, h.confirmedPlan(late), branch).andExpect(status().isBadRequest());
    }

    /** Hai lần ngừng chéo nhau (A→B và B→A) khóa chi nhánh theo thứ tự id nên không khóa chết (không có 500). */
    @Test
    void crossDeactivate_noDeadlock() throws Exception {
        String admin = h.admin();
        int a = newBranch(admin, "KH03");
        int b = newBranch(admin, "KH04");
        CountDownLatch gate = new CountDownLatch(1);
        Future<MockHttpServletResponse> f1 = pool.submit(() -> {
            gate.await();
            return h.send(post("/admin/branches/" + a + "/deactivate"), Map.of("transferToBranchId", b), admin).andReturn().getResponse();
        });
        Future<MockHttpServletResponse> f2 = pool.submit(() -> {
            gate.await();
            return h.send(post("/admin/branches/" + b + "/deactivate"), Map.of("transferToBranchId", a), admin).andReturn().getResponse();
        });
        gate.countDown();
        // Không có hồ sơ hay nhân viên nên không cần chi nhánh nhận: cả hai thành công, quan trọng là không khóa chết.
        assertThat(f1.get(60, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        assertThat(f2.get(60, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
    }
}
