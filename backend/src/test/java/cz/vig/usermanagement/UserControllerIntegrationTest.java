package cz.vig.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end tests of the REST API against a real PostgreSQL (Testcontainers),
 * so validation, constraints and Flyway migrations are exercised together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    // --- create ---------------------------------------------------------------------------

    @Test
    void createReturns201WithLocationAndNormalizedEmail() throws Exception {
        create("Alice", "  Alice@Example.COM ")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/users/" + lastId())))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createWithInvalidEmailOrBlankNameReturns400WithFieldErrors() throws Exception {
        create(" ", "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        assertThat(repository.count()).isZero();
    }

    @Test
    void createWithDuplicateEmailReturns409EvenWithDifferentCase() throws Exception {
        create("Alice", "alice@example.com").andExpect(status().isCreated());

        create("Other Alice", "ALICE@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User with email alice@example.com already exists"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createIgnoresClientSuppliedId() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 999, "name": "Alice", "email": "alice@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999)));
    }

    // --- read -----------------------------------------------------------------------------

    @Test
    void listReturnsAllUsers() throws Exception {
        create("Alice", "alice@example.com");
        create("Bob", "bob@example.com");

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Bob"));
    }

    @Test
    void getUnknownUserReturns404() throws Exception {
        mvc.perform(get("/api/users/{id}", 12345))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User 12345 not found"));
    }

    // --- update ---------------------------------------------------------------------------

    @Test
    void updateChangesNameAndIncrementsVersion() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");

        update(id, """
                {"name": "Alice Smith", "version": 0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(repository.findById(id)).get()
                .extracting(User::getName).isEqualTo("Alice Smith");
    }

    @Test
    void updateWithDifferentEmailIsRejectedAndEmailStaysUnchanged() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");

        update(id, """
                {"name": "Alice Smith", "email": "hacker@example.com", "version": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email cannot be changed after the user is created"));

        User stored = repository.findById(id).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.getName()).isEqualTo("Alice");
    }

    @Test
    void updateWithSameEmailIsAccepted() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");

        update(id, """
                {"name": "Alice Smith", "email": "Alice@Example.com", "version": 0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");
        update(id, """
                {"name": "First edit", "version": 0}""").andExpect(status().isOk());

        update(id, """
                {"name": "Second edit based on old data", "version": 0}""")
                .andExpect(status().isConflict());

        assertThat(repository.findById(id)).get()
                .extracting(User::getName).isEqualTo("First edit");
    }

    @Test
    void updateWithBlankNameOrMissingVersionReturns400() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");

        update(id, """
                {"name": ""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void updateUnknownUserReturns404() throws Exception {
        update(12345, """
                {"name": "Nobody", "version": 0}""")
                .andExpect(status().isNotFound());
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    void deleteReturns204AndUserIsGone() throws Exception {
        long id = createAndGetId("Alice", "alice@example.com");

        mvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownUserReturns404() throws Exception {
        mvc.perform(delete("/api/users/{id}", 12345)).andExpect(status().isNotFound());
    }

    // --- helpers --------------------------------------------------------------------------

    private ResultActions create(String name, String email) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s"}""".formatted(name, email)));
    }

    private long createAndGetId(String name, String email) throws Exception {
        String body = create(name, email).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions update(long id, String json) throws Exception {
        return mvc.perform(put("/api/users/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long lastId() {
        return repository.findAll().getLast().getId();
    }
}
