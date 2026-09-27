package studio.aakar.api.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes RFC 9457 Problem Details from inside the security filter chain, where no controller advice runs. */
@Component
class ProblemResponses {

    private static final String PROBLEM_TYPE_BASE = "https://aakar.studio/problems/";

    private final ObjectMapper json;

    ProblemResponses(ObjectMapper json) {
        this.json = json;
    }

    void write(HttpServletResponse response, HttpStatus status, String code, String title, String detail) throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", PROBLEM_TYPE_BASE + code);
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }
}
