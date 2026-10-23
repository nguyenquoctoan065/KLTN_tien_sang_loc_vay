package vn.kltn.tiensanglocvay.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B4: /notifications, /notifications/{id}/read, /notifications/read-all theo fe/src/mocks/handlers/me.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NotificationApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    NotificationService notifications;

    @Autowired
    JdbcTemplate jdbc;

    private String login(String kind, String username) throws Exception {
        String b = "{\"kind\":\"" + kind + "\",\"username\":\"" + username + "\",\"password\":\"123456\"}";
        return JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(b))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private long idOf(String title) {
        return jdbc.queryForObject("select id from notification where title = ?", Long.class, title);
    }

    @Test
    void listReadAndReadAllByRecipient() throws Exception {
        long customerId = jdbc.queryForObject("select id from customer where phone = '0901234567'", Long.class);
        notifications.toCustomer(customerId, "KH-1", "Nội dung 1", "/ho-so/x");
        notifications.toCustomer(customerId, "KH-2", "Nội dung 2", null);
        notifications.toBranch(1, "CN-HN", "Hồ sơ mới", "/noi-bo/ho-so/x");
        notifications.toBranch(2, "CN-HCM", "Hồ sơ mới", null);
        String customer = login("CUSTOMER", "0901234567");
        String hn = login("STAFF", "nhanvien@tiensangloc.local");
        String hcm = login("STAFF", "nv.hcm@tiensangloc.local");
        String admin = login("STAFF", "quantri@tiensangloc.local");

        mvc.perform(get("/notifications").header("Authorization", "Bearer " + customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("KH-2"))
                .andExpect(jsonPath("$[0].link").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[1].title").value("KH-1"))
                .andExpect(jsonPath("$[1].body").value("Nội dung 1"))
                .andExpect(jsonPath("$[1].link").value("/ho-so/x"))
                .andExpect(jsonPath("$[1].read").value(false))
                .andExpect(jsonPath("$[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].customerId").doesNotExist());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hn))
                .andExpect(jsonPath("$[*].title").value(Matchers.contains("CN-HN")));
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hcm))
                .andExpect(jsonPath("$[*].title").value(Matchers.contains("CN-HCM")));
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Chưa đăng nhập."));

        // Thông báo của người khác bị bỏ qua (vẫn 200)
        mvc.perform(post("/notifications/" + idOf("KH-1") + "/read").header("Authorization", "Bearer " + hn))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/notifications/999999/read").header("Authorization", "Bearer " + customer)).andExpect(status().isOk());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + customer)).andExpect(jsonPath("$[1].read").value(false));
        mvc.perform(post("/notifications/" + idOf("KH-1") + "/read").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$[0].read").value(false)).andExpect(jsonPath("$[1].read").value(true));
        mvc.perform(post("/notifications/" + idOf("CN-HN") + "/read").header("Authorization", "Bearer " + hcm)).andExpect(status().isOk());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hn)).andExpect(jsonPath("$[0].read").value(false));

        mvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + customer))
                .andExpect(jsonPath("$[*].read").value(Matchers.contains(true, true)));
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hn)).andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + hn)).andExpect(status().isOk());
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hn)).andExpect(jsonPath("$[0].read").value(true));
        mvc.perform(get("/notifications").header("Authorization", "Bearer " + hcm)).andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }
}
