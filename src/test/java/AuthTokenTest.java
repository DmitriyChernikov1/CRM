import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;
import static io.restassured.RestAssured.given;

public class AuthTokenTest {

    private static final String BASE_URL = "https://preprod-crm.sbercity.ru";

    private static RequestSpecification authRequest() {
        return given()
                .baseUri(BASE_URL)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("accept", "application/json")
                .header("Accept-Language", "ru-RU")
                .relaxedHTTPSValidation();
    }

    public static String getAuthToken() {  // Изменено void на String
        Map<String, String> authBody = new HashMap<>();
        authBody.put("email", "admin@admin.ru");
        authBody.put("password", "&q38(N3z3ONgLt&q2qSv&+JBGW");

        return authRequest()  // Используем созданный метод
                .body(authBody)
                .post("/api/v1/auth/signin")  // Исправлена кавычка
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("token");
    }
}