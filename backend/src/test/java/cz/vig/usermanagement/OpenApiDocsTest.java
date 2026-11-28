package cz.vig.usermanagement;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Guards the generated spec against drifting from the real status codes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void specDocumentsRealStatusCodesAndErrors() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("User Management API"))
                .andExpect(jsonPath("$.paths['/api/users'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/users'].post.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.responses", hasKey("204")))
                .andExpect(jsonPath("$.paths['/api/users'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users'].post.responses['409'].content", hasKey("application/problem+json")));
    }
}
