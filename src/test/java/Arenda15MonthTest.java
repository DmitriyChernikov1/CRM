import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Arenda15MonthTest {
    private static String accessToken;
    private static String dynamicEmail;
    private static String dynamicName;
    private static String dynamicSurname;

    // сквозной id лида аренды, чтобы шаги можно было гонять и по отдельности, и цепочкой
    private static int rentInterestId;
    private static int contactId;
    private static String securityClearanceCertUuid;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        RestAssured.useRelaxedHTTPSValidation();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        dynamicEmail = "autotest." + uniqueSuffix + "@gmail.com";
        dynamicName = "Дмитрий";
        dynamicSurname = "Черников";
    }

    @Test
    @Order(1)
    public void auth() {
        Map<String, String> authBody = new HashMap<>();
        authBody.put("email", "admin@admin.ru");
        authBody.put("password", "&q38(N3z3ONgLt&q2qSv&+JBGW");

        Response auth = given()
                .contentType("application/json")
                .body(authBody)
                .when()
                .post("/api/v1/auth/signin")
                .then()
                .statusCode(200)
                .extract().response();

        // ВАЖНО: присваиваем значение в статическую переменную класса,
        // а не создаём локальную (иначе accessToken класса останется null)
        accessToken = auth.path("token");
    }

    @Test
    @Order(2)
    public void createRentInterest() {
        rentInterestId = createRentInterest(dynamicName, dynamicSurname, dynamicEmail);
        System.out.println("Создан лид аренды КН, id = " + rentInterestId);
    }

    @Test
    @Order(3)
    public void updateAnketa() {
        Map<String, Object> anketa = new HashMap<>();
        anketa.put("businessTypeId", 3);
        anketa.put("complexIds", List.of(5));
        anketa.put("squareTotalRangeId", 3);
        anketa.put("rentalPeriod", 15);
        anketa.put("interestCategoryId", 2);
        anketa.put("informationSourceCategoryId", 2);

        Map<String, Object> body = new HashMap<>();
        body.put("anketa", anketa);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body(body)
                .when()
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(4)
    public void markAsInterest() {
        Response response = given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/interest")
                .then()
                .statusCode(200)
                .body("status.name", equalTo("Интерес"))
                .extract().response();

        contactId = response.path("contact.id");
        System.out.println("Создан контакт, id = " + contactId);
    }

    @Test
    @Order(5)
    public void uploadSecurityClearanceCert() {
        // положите тестовый файл рядом, например src/test/resources/propiska.jpg
        File file = new File("src/test/resources/propiska.pdf");

        Response response = given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", file, "image/jpeg")
                .when()
                .post("/api/v1/commercial-rent-interests/upload")
                .then()
                .statusCode(200)
                .extract().response();

        securityClearanceCertUuid = response.path("uuid");
    }

    @Test
    @Order(6)
    public void attachContactAndDocument() {
        Map<String, Object> body = new HashMap<>();
        body.put("applicationName", dynamicName);
        body.put("contactId", contactId);
        body.put("presentationMaterials", List.of());
        body.put("securityClearanceCert", List.of(securityClearanceCertUuid));
        body.put("additionalContacts", List.of());
        body.put("responsible", Map.of("responsibleType", "USER", "responsibleId", 1));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body(body)
                .when()
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(7)
    public void moveToProjectApproval() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/project-approval")
                .then()
                .statusCode(200)
                .body("status.name", equalTo("Утверждение проекта"));
    }

    @Test
    @Order(8)
    public void setCommissionApproval() {
        Map<String, Object> body = new HashMap<>();
        body.put("applicationName", dynamicName);
        body.put("contactId", contactId);
        body.put("commissionApproval", "test");
        body.put("presentationMaterials", List.of());
        body.put("securityClearanceCert", List.of(securityClearanceCertUuid));
        body.put("additionalContacts", List.of());
        body.put("responsible", Map.of("responsibleType", "USER", "responsibleId", 1));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body(body)
                .when()
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(9)
    public void agreeRentInterest() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/agreed")
                .then()
                .statusCode(200)
                .body("status.name", equalTo("Согласован"));
    }

    /**
     * Переиспользуемый метод создания лида аренды КН (по аналогии с createLead()) —
     * удобно вызывать из других тестов, где нужен готовый rentInterestId.
     */
    private static int createRentInterest(String name, String surname, String email) {
        Map<String, Object> extContact = new HashMap<>();
        extContact.put("name", name);
        extContact.put("surname", surname);
        extContact.put("contactsInfoList", List.of(
                Map.of("contactsInfoTypeId", 2, "name", email, "isConfirm", false)
        ));

        Map<String, Object> anketa = new HashMap<>();
        anketa.put("businessTypeId", 3);
        anketa.put("complexIds", List.of(5));
        anketa.put("rentalPeriod", 15);
        anketa.put("interestCategoryId", 2);

        Map<String, Object> body = new HashMap<>();
        body.put("dealTypeId", 1);
        body.put("extContact", extContact);
        body.put("anketa", anketa);
        body.put("analytics", Map.of());
        body.put("responsible", Map.of("responsibleType", "USER", "responsibleId", 1));

        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body(body)
                .when()
                .post("/api/v1/commercial-rent-interests")
                .then()
                .statusCode(200)
                .extract().path("id");
    }
    @Test
    @Order(10)
    public void RentList(){
        String body = "{\"page\":1,\"size\":25,\"filter\":{\"rentalStatusIds\":[1]}}";
        Response RentList = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/commercial/rent/list")
                .andReturn();
        String RentON = RentList.jsonPath().getString("data.id[0]");
        System.out.println(RentON);
        RentList.prettyPrint();
    }
}