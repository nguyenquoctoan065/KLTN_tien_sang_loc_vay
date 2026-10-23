package vn.kltn.tiensanglocvay.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Đối chứng Java với fe/src/mocks/engine.ts: mỗi file trong src/test/resources/engine-fixtures (sinh bằng
 * be/tools/gen-engine-fixtures.ts) là một ca. Tiền, kỳ hạn, chuỗi phải bằng tuyệt đối; tỷ lệ % và điểm lệch tối đa 0,05.
 */
class EngineFixtureTest {

    private static final Path DIR = Path.of("src/test/resources/engine-fixtures");
    private static final Set<String> RATIO_KEYS = Set.of("dti", "currentDti", "dtiAfter", "dtiMax", "score", "rate", "refRate");
    private static final double TOLERANCE = 0.05;

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void hasEnoughFixtures() throws IOException {
        try (Stream<Path> files = Files.list(DIR)) {
            assertThat(files.filter(p -> p.toString().endsWith(".json")).count()).isGreaterThanOrEqualTo(30);
        }
    }

    @TestFactory
    Stream<DynamicTest> javaEqualsTypeScript() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(DIR)) {
            files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        return files.stream().map(f -> DynamicTest.dynamicTest(f.getFileName().toString(), () -> check(f)));
    }

    private static void check(Path file) throws IOException {
        JsonNode fx;
        try (InputStream in = Files.newInputStream(file)) {
            fx = JSON.readTree(in);
        }
        LocalDate today = LocalDate.parse(fx.get("today").asText());
        ParamSet ps = JSON.treeToValue(fx.get("params"), ParamSet.class);
        WeightSet ws = new WeightSet(JSON.convertValue(fx.get("weights"), new TypeReference<LinkedHashMap<String, Map<String, Double>>>() {
        }));
        List<CrossSell> cs = JSON.convertValue(fx.get("crossSells"), new TypeReference<List<CrossSell>>() {
        });
        EngineInput inp = JSON.treeToValue(fx.get("input"), EngineInput.class);

        List<String> diffs = new ArrayList<>();
        compare("screening", fx.get("screening"), JSON.valueToTree(Engine.screen(inp, ps, ws, today)), diffs);
        int i = 0;
        for (JsonNode sim : fx.get("simulations")) {
            SimulationInput req = JSON.treeToValue(sim.get("request"), SimulationInput.class);
            compare("simulations[" + i++ + "]", sim.get("expected"), JSON.valueToTree(Engine.simulate(inp, ps, ws, req, cs, today)), diffs);
        }
        assertThat(diffs).as("khác biệt giữa Java và engine.ts trong " + file.getFileName()).isEmpty();
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> diffs) {
        if (expected == null || expected.isNull()) {
            if (actual != null && !actual.isNull()) {
                diffs.add(path + ": mong đợi null, Java " + actual);
            }
            return;
        }
        if (actual == null || actual.isNull()) {
            diffs.add(path + ": mong đợi " + expected + ", Java null");
            return;
        }
        if (expected.isObject()) {
            Iterator<String> names = expected.fieldNames();
            while (names.hasNext()) {
                String k = names.next();
                compare(path + "." + k, expected.get(k), actual.get(k), diffs);
            }
            actual.fieldNames().forEachRemaining(k -> {
                if (!expected.has(k)) {
                    diffs.add(path + "." + k + ": thừa trường ở Java");
                }
            });
        } else if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                diffs.add(path + ": mong đợi " + expected.size() + " phần tử, Java " + actual.size() + " – " + expected + " / " + actual);
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "[" + i + "]", expected.get(i), actual.get(i), diffs);
            }
        } else if (expected.isNumber()) {
            String key = path.substring(path.lastIndexOf('.') + 1);
            double tol = RATIO_KEYS.contains(key) ? TOLERANCE : 0;
            if (!actual.isNumber() || Math.abs(expected.asDouble() - actual.asDouble()) > tol) {
                diffs.add(path + ": mong đợi " + expected + ", Java " + actual);
            }
        } else if (!expected.equals(actual)) {
            diffs.add(path + ": mong đợi " + expected + ", Java " + actual);
        }
    }
}
