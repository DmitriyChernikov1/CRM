import io.qameta.allure.Description;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Arenda15MonthTest {
    private static String accessToken;
    private static String dynamicEmail;
    private static String dynamicName;
    private static String dynamicSurname;

    // сквозной id лида аренды, чтобы шаги можно было гонять и по отдельности, и цепочкой
    private static Integer rentInterestId;
    private static Integer contactId;
    private static String securityClearanceCertUuid;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        // Отключаю проверку SSL-сертификата
        RestAssured.useRelaxedHTTPSValidation();

        accessToken = new AuthTokenTest().getAuthToken();

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        dynamicEmail = "autotest." + uniqueSuffix + "@gmail.com";
        dynamicName = "Дмитрий";
        dynamicSurname = "Черников";
    }

    private static String buildRentInterestBody(String name, String surname, String email) {
        return String.format(
                "{\"dealTypeId\":1,\"extContact\":{\"name\":\"%s\",\"surname\":\"%s\"," +
                        "\"contactsInfoList\":[{\"contactsInfoTypeId\":2,\"name\":\"%s\",\"isConfirm\":false}]}," +
                        "\"anketa\":{\"businessTypeId\":3,\"complexIds\":[5],\"rentalPeriod\":15," +
                        "\"interestCategoryId\":2},\"analytics\":{}," +
                        "\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}}",
                name, surname, email
        );
    }

    private static String buildAnketaBody() {
        return "{\"anketa\":{\"businessTypeId\":3,\"complexIds\":[5],\"squareTotalRangeId\":3," +
                "\"rentalPeriod\":15,\"interestCategoryId\":2,\"informationSourceCategoryId\":2}}";
    }

    private static String buildAttachContactBody(String applicationName, Integer contactId, String certUuid) {
        return String.format(
                "{\"applicationName\":\"%s\",\"contactId\":%d,\"presentationMaterials\":[]," +
                        "\"securityClearanceCert\":[\"%s\"],\"additionalContacts\":[]," +
                        "\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}}",
                applicationName, contactId, certUuid
        );
    }

    private static String buildCommissionApprovalBody(String applicationName, Integer contactId, String certUuid) {
        return String.format(
                "{\"applicationName\":\"%s\",\"contactId\":%d,\"commissionApproval\":\"test\"," +
                        "\"presentationMaterials\":[],\"securityClearanceCert\":[\"%s\"]," +
                        "\"additionalContacts\":[],\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}}",
                applicationName, contactId, certUuid
        );
    }

    @Test
    @Order(1)
    @Description("Создание лида аренды КН с динамичными данными")
    @DisplayName("Создание лида аренды")
    public void createRentInterest() {
        String body = buildRentInterestBody(dynamicName, dynamicSurname, dynamicEmail);

        Response createRentInterest = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/commercial-rent-interests")
                .andReturn();

        int statusCode = createRentInterest.getStatusCode();
        assertEquals(200, statusCode);

        rentInterestId = createRentInterest.jsonPath().getInt("id");
        assertNotNull(rentInterestId, "ID созданного лида аренды не должен быть null");
        assertTrue(rentInterestId > 0, "ID созданного лида аренды должен быть положительным числом");

        System.out.println("Создан лид аренды КН, id = " + rentInterestId);
    }

    @Test
    @Order(2)
    @Description("Заполнение анкеты лида аренды")
    @DisplayName("Обновление анкеты")
    public void updateAnketa() {
        String body = buildAnketaBody();

        Response updateAnketa = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .andReturn();

        assertEquals(200, updateAnketa.getStatusCode());
    }

    @Test
    @Order(3)
    @Description("Перевод лида аренды в статус 'Интерес' и создание контакта")
    @DisplayName("Отметка интереса")
    public void markAsInterest() {
        Response markAsInterest = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/interest")
                .andReturn();

        assertEquals(200, markAsInterest.getStatusCode());

        String statusName = markAsInterest.jsonPath().getString("status.name");
        assertEquals("Интерес", statusName, "После interest лид должен перейти в статус 'Интерес'");

        contactId = markAsInterest.jsonPath().getInt("contact.id");
        assertNotNull(contactId, "id контакта не должен быть null");
        System.out.println("Создан контакт, id = " + contactId);
    }

    @Test
    @Order(4)
    @Description("Загрузка справки для проверки службой безопасности")
    @DisplayName("Загрузка документа СБ")
    public void uploadSecurityClearanceCert() {
        // положите тестовый файл рядом, например src/test/resources/propiska.pdf
        File file = new File("src/test/resources/propiska.pdf");

        Response uploadResponse = RestAssured
                .given()
                .multiPart("file", file, "image/jpeg")
                .headers("Authorization", "Bearer " + accessToken)
                .post("/api/v1/commercial-rent-interests/upload")
                .andReturn();

        assertEquals(200, uploadResponse.getStatusCode());

        securityClearanceCertUuid = uploadResponse.jsonPath().getString("uuid");
        assertNotNull(securityClearanceCertUuid, "Загруженный файл должен получить UUID");
    }

    @Test
    @Order(5)
    @Description("Прикрепление контакта и документа СБ к лиду аренды")
    @DisplayName("Прикрепление контакта и документа")
    public void attachContactAndDocument() {
        String body = buildAttachContactBody(dynamicName, contactId, securityClearanceCertUuid);

        Response attachResponse = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .andReturn();

        assertEquals(200, attachResponse.getStatusCode());
    }

    @Test
    @Order(6)
    @Description("Перевод лида аренды в статус 'Утверждение проекта'")
    @DisplayName("Перевод на утверждение проекта")
    public void moveToProjectApproval() {
        Response moveResponse = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/project-approval")
                .andReturn();

        assertEquals(200, moveResponse.getStatusCode());

        String statusName = moveResponse.jsonPath().getString("status.name");
        assertEquals("Утверждение проекта", statusName,
                "После project-approval лид должен перейти в статус 'Утверждение проекта'");
    }

    @Test
    @Order(7)
    @Description("Заполнение согласования комиссии")
    @DisplayName("Согласование комиссии")
    public void setCommissionApproval() {
        String body = buildCommissionApprovalBody(dynamicName, contactId, securityClearanceCertUuid);

        Response commissionResponse = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/commercial-rent-interests/" + rentInterestId)
                .andReturn();

        assertEquals(200, commissionResponse.getStatusCode());
    }

    @Test
    @Order(8)
    @Description("Перевод лида аренды в статус 'Согласован'")
    @DisplayName("Согласование лида аренды")
    public void agreeRentInterest() {
        Response agreeResponse = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .post("/api/v1/commercial-rent-interests/" + rentInterestId + "/agreed")
                .andReturn();

        assertEquals(200, agreeResponse.getStatusCode());

        String statusName = agreeResponse.jsonPath().getString("status.name");
        assertEquals("Согласован", statusName, "После agreed лид должен перейти в статус 'Согласован'");
    }

    @Test
    @Order(9)
    @Description("Получение списка лидов аренды по фильтру статуса")
    @DisplayName("Список ОН")
    public void rentList() {
        String body = "{\"page\":1,\"size\":25,\"filter\":{\"rentalStatusIds\":[1]}}";

        Response rentListResponse = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/commercial/rent/list")
                .andReturn();

        assertEquals(200, rentListResponse.getStatusCode());

        String firstId = rentListResponse.jsonPath().getString("data.id[0]");
        System.out.println(firstId);
        rentListResponse.prettyPrint();
    }
}