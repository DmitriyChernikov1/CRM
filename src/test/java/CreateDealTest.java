import io.qameta.allure.Description;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateDealTest {
    private static String accessToken;
    private static final Logger log = LoggerFactory.getLogger(CreateLidAndContactTest.class);
    private static String dynamicEmail;
    private static String dynamicPhone;
    private static String dynamicName;
    private static String dynamicSurname;
    private static Integer createdInterestId;
    private static Integer createdContactId;

    @BeforeAll
    static void setup() {

        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        // Отключаю проверку SSL-сертификата

        RestAssured.useRelaxedHTTPSValidation();

        accessToken = new AuthTokenTest().getAuthToken();


        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        dynamicEmail = "autotest." + uniqueSuffix + "@gmail.com";
        dynamicPhone = "+7" + (9000000000L + ThreadLocalRandom.current().nextLong(100000000L));
        dynamicName = "Дмитрий" + uniqueSuffix;
        dynamicSurname = "Черников" + uniqueSuffix;
    }
    private static String buildInterestBody(String name, String surname, String email) {
        return String.format(
                "{\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}," +
                        "\"extContact\":{\"name\":\"%s\",\"surname\":\"%s\"," +
                        "\"contactsInfoList\":[{\"contactsInfoTypeId\":2,\"name\":\"%s\",\"isConfirm\":false}]}}",
                name, surname, email
        );
    }

    private static String buildRequisitesBody(Integer contactId, LocalDate birthDate, LocalDate issueDate) {
        return String.format(
                "{\"personalData\":{\"citizenshipId\":1,\"documentSeries\":\"00 00\",\"documentNumber\":\"000000\"," +
                        "\"issueAuthority\":\"000000000000\",\"issueDate\":\"%s\",\"issueDepartment\":\"000-000\"," +
                        "\"gender\":\"MALE\",\"birthDate\":\"%s\",\"birthPlace\":\"000000000000\"," +
                        "\"registrationAddress\":\"0000000000000000000\"," +
                        "\"livingAddress\":\"Московская обл, г Реутов, шоссе Автомагистраль Москва-Нижний Новгород\"," +
                        "\"inn\":\"000000000000\",\"snils\":\"000-000-000 00\"},\"bankAccount\":{}," +
                        "\"requisitesTypeId\":1,\"documentTypeId\":1,\"contactId\":%d}",
                issueDate, birthDate, contactId
        );
    }

    @Test
    @Order(1)
    @Description("Провуерка дубликатов email,телефонов")
    @DisplayName("проверка дубликатов перед созданием лида")
    public void CheckDuplicate(){
        String body = String.format("{\"phones\":[\"%s\"],\"emails\":[]}", dynamicPhone);

        Response duplicatesPhone = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();
        int statuscode = duplicatesPhone.getStatusCode();
        assertEquals (200,statuscode);
        int totalCount = duplicatesPhone.jsonPath().getInt("totalCount");
        assertEquals(0,totalCount,"дубль телефона");
        System.out.println(body);


        String body2 = String.format(
                "{\"phones\":[],\"emails\":[\"%s\"]}",
                dynamicEmail
        );

        Response duplicatesEmail = RestAssured
                .given()
                .body(body2)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();
        //проверки
        int statusCodes = duplicatesEmail.getStatusCode();
        assertEquals(200, statusCodes);

        int totalCounts = duplicatesEmail.jsonPath().getInt("totalCount");
        assertEquals(0, totalCounts, "Дубль почты");
        System.out.println(body2);
    }
    @Test
    @Order(2)
    @Description("создание лида с динамичными данными")
    @DisplayName("Создание лида")
    public void createInterest() {
        String body = buildInterestBody(dynamicName, dynamicSurname, dynamicEmail);

        Response createInterest = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest")
                .andReturn();

        //Проверки
        int statusCode = createInterest.getStatusCode();
        assertEquals(200, statusCode);

        createdInterestId = createInterest.jsonPath().getInt("id");
        assertNotNull(createdInterestId, "ID созданного лида не должен быть null");
        assertTrue(createdInterestId > 0, "ID созданного лида должен быть положительным числом");

        String returnedEmail = createInterest.jsonPath()
                .getString("extContact.extContactInfo[0].name");
        assertEquals(dynamicEmail, returnedEmail, "Email в ответе должен совпадать с отправленным");

        String status = createInterest.jsonPath().getString("status.name");
        assertNotNull(status, "У созданного лида должен быть статус");
        System.out.println(dynamicEmail);


        System.out.println(createdInterestId);
    }
    @Test
    @Order(3)
    @Description("создание контакта с динамичными данными")
    @DisplayName("Создание контакта")
    public void createContact(){
        Response createContact = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/" + createdInterestId + "/contact_create")
                .andReturn();

        //проверки
        int statusCode = createContact.getStatusCode();
        assertEquals(200,statusCode);

        createdContactId = createContact.jsonPath().getInt("contact.id");
        assertNotNull(createdContactId,"id не должен быть null");
        System.out.println(createContact);
    }
    @Test
    @Order(4)
    @Description("Создание анкеты физ лица")
    @DisplayName("создание анкеты")
    public void CreateRequisites(){
        LocalDate birthDate = LocalDate.now().minusYears(25);
        LocalDate issueDate = LocalDate.now().minusYears(5);

        String body = buildRequisitesBody(createdContactId, birthDate, issueDate);

        Response createRequisites = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/requisites")
                .andReturn();

        //проверки
        int statusCode = createRequisites.getStatusCode();
        assertEquals(200, statusCode);

        Integer requisitesId = createRequisites.jsonPath().getInt("id");
        assertNotNull(requisitesId, "ID созданной анкеты не должен быть null");
    }

}