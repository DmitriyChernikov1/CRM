import io.qameta.allure.Description;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateDealTest {
    private static Integer createdRequisitesId;
    private static Integer finishingId;
    private static Integer meetTypeId;
    private static Integer createdMortgageId;
    private static Integer createdDealId;
    private static String accessToken;
    private static final Logger log = LoggerFactory.getLogger(CreateLidAndContactTest.class);
    private static String dynamicEmail;
    private static String dynamicPhone;
    private static String dynamicName;
    private static String dynamicSurname;
    private static Integer createdInterestId;
    private static Integer createdContactId;
    private static Integer idRealEstate;

    @BeforeAll
    static void setup() {

        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        // Отключаю проверку SSL-сертификата

        RestAssured.useRelaxedHTTPSValidation();

        accessToken = new AuthTokenTest().getAuthToken();


        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        dynamicEmail = "autotest." + uniqueSuffix + "@gmail.com";
        dynamicPhone = "+7" + (9000000000L + ThreadLocalRandom.current().nextLong(100000000L));
        dynamicName = "Дмитрий";
        dynamicSurname = "Черников";
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

    @ParameterizedTest
    @ValueSource(strings = {"phone", "email"})
    @Order(1)
    @Description("Проверка дубликатов email, телефонов")
    @DisplayName("проверка дубликатов перед созданием лида")
    public void CheckDuplicate(String type) {
        String body;

        if ("phone".equals(type)) {
            body = String.format("{\"phones\":[\"%s\"],\"emails\":[]}", dynamicPhone);
        } else {
            body = String.format("{\"phones\":[],\"emails\":[\"%s\"]}", dynamicEmail);
        }

        Response response = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();

        int statuscode = response.getStatusCode();
        assertEquals(200, statuscode);

        int totalCount = response.jsonPath().getInt("totalCount");
        assertEquals(0, totalCount, "Дубль " + type);
        System.out.println(body);
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
    @Test
    @Order(5)
    @Description("Поиск ОН")
    @DisplayName("Поиск ОН")
    public void catalogList(){
        String body = "{\"page\":1,\"size\":100,\"sortBy\":[{\"property\":\"status\",\"direction\":\"ASC\"}],\"filter\":{\"complexId\":16,\"price\":{\"from\":100000},\"statusIds\":[1]}}";
        Response list = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/real_estate/catalog/list")
                .andReturn();
        int statusCode =list.getStatusCode();
        assertEquals(200,statusCode);
        int totalCount =list.jsonPath().getInt("totalCount");
        assertTrue(totalCount>0);
        idRealEstate = list.jsonPath().getInt("data.id[0]");
        System.out.println(idRealEstate);

    }
    @Test
    @Order(6)
    @Description("Перевод статуса лида")
    @DisplayName("Лид в статус интерес")
    public void interestLead (){

        Response like = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/"+ createdInterestId +"/interest")
                .andReturn();
        int statuscode =like.getStatusCode();
        assertEquals(200,statuscode);

    }
    @Test
    @Order(7)
    @Description("добавление ОН")
    @DisplayName("Лайк ОН")
    public void likesON(){
        String body = "[{\"objectTypeId\":1,\"objectId\":" + idRealEstate + "}]";
        Response like = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .patch("/api/v1/interest/" + createdInterestId + "/likes")
                .andReturn();
        int statuscode =like.getStatusCode();
        assertEquals(200,statuscode);
        String error = like.getBody().asString();
        System.out.println(error);

    }
    @Test
    @Order(8)
    @Description("Создание сделки на основе объекта недвижимости и лида")
    @DisplayName("Создание сделки")
    public void createDeal() {
        String body = String.format(
                "{\"objectTypeId\":1,\"objectId\":%d,\"interestId\":%d,\"reservationType\":1,\"payType\":3,\"objectFixedPrice\":true}",
                idRealEstate, createdInterestId
        );

        Response createDeal = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/deal")
                .andReturn();

        int statusCode = createDeal.getStatusCode();
        assertEquals(200, statusCode);

        createdDealId = createDeal.jsonPath().getInt("id");
        assertNotNull(createdDealId, "ID созданной сделки не должен быть null");
        assertTrue(createdDealId > 0, "ID сделки должен быть положительным числом");

        String dealStatus = createDeal.jsonPath().getString("status.name");
        assertNotNull(dealStatus, "У сделки должен быть статус");

        Integer parentInterestId = createDeal.jsonPath().getInt("parentInterest.id");
        assertEquals(createdInterestId, parentInterestId, "Сделка должна ссылаться на исходный лид");

        System.out.println("Создана сделка: " + createdDealId);
    }

    @Test
    @Order(9)
    @Description("Получение сделки по id")
    @DisplayName("Получение сделки")
    public void getDeal() {
        Response getDeal = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/deal/" + createdDealId)
                .andReturn();

        assertEquals(200, getDeal.getStatusCode());
        assertEquals(createdDealId, getDeal.jsonPath().getInt("id"), "ID в ответе должен совпадать");
    }

    @Test
    @Order(10)
    @Description("Проверка количества активных договоров по сделке")
    @DisplayName("Активные договоры сделки")
    public void checkActiveAgreementsCount() {
        Response response = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/deal/" + createdDealId + "/activeAgreementsCount")
                .andReturn();

        assertEquals(200, response.getStatusCode());
    }
    @Test
    @Order(11)
    @Description("Проверка списка ипотек по сделке перед созданием")
    @DisplayName("Список ипотек сделки (пустой)")
    public void checkMortgagesListEmpty() {
        String body = "{\"page\":1,\"size\":100,\"sortBy\":[{\"property\":\"crDate\",\"direction\":\"DESC\"}]}";

        Response mortgages = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/deal/" + createdDealId + "/mortgages")
                .andReturn();

        assertEquals(200, mortgages.getStatusCode());
        int totalCount = mortgages.jsonPath().getInt("totalCount");
        assertEquals(0, totalCount, "У новой сделки не должно быть ипотек");
    }

    @Test
    @Order(12)
    @Description("Создание ипотеки по сделке")
    @DisplayName("Создание ипотеки")
    public void createMortgage() {
        String body = String.format(
                "{\"provider\":1,\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}," +
                        "\"relations\":[{\"typeId\":1,\"relatedId\":%d},{\"typeId\":2,\"relatedId\":%d},{\"typeId\":3,\"relatedId\":%d}]}",
                createdInterestId, createdContactId, createdDealId
        );

        Response createMortgage = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/mortgage")
                .andReturn();

        int statusCode = createMortgage.getStatusCode();
        assertEquals(200, statusCode);

        createdMortgageId = createMortgage.jsonPath().getInt("id");
        assertNotNull(createdMortgageId, "ID созданной ипотеки не должен быть null");
        assertTrue(createdMortgageId > 0, "ID ипотеки должен быть положительным числом");

        String mortgageStatus = createMortgage.jsonPath().getString("status.name");
        assertNotNull(mortgageStatus, "У ипотеки должен быть статус");

        String bankStatus = createMortgage.jsonPath().getString("bankStatus.name");
        assertNotNull(bankStatus, "У ипотеки должен быть банковский статус");

        System.out.println("Создана ипотека: " + createdMortgageId);
    }

    @Test
    @Order(13)
    @Description("Проверка связей созданной ипотеки с лидом, контактом и сделкой")
    @DisplayName("Проверка связей ипотеки")
    public void getMortgageAndCheckRelations() {
        Response getMortgage = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/mortgage/" + createdMortgageId)
                .andReturn();

        assertEquals(200, getMortgage.getStatusCode());
        assertEquals(createdMortgageId, getMortgage.jsonPath().getInt("id"));

        java.util.List<Integer> relatedIds = getMortgage.jsonPath().getList("crmRelations.relatedId", Integer.class);
        assertTrue(relatedIds.contains(createdDealId), "Ипотека должна быть связана со сделкой");
        assertTrue(relatedIds.contains(createdInterestId), "Ипотека должна быть связана с лидом");
        assertTrue(relatedIds.contains(createdContactId), "Ипотека должна быть связана с контактом");
    }
    @Test
    @Order(14)
    @Description("Проверка, что первая встреча по сделке ещё не проведена")
    @DisplayName("Проверка первой встречи (до)")
    public void checkFirstMeetingNotCompleted() {
        Response check = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/deal/" + createdDealId + "/first-meeting/check")
                .andReturn();

        assertEquals(200, check.getStatusCode());
        assertFalse(check.jsonPath().getBoolean("hasCompletedFirstMeeting"),
                "Изначально первая встреча не должна быть проведена");
    }

    @Test
    @Order(15)
    @Description("Получение актуального id типа встречи 'Первичная встреча' из справочника")
    @DisplayName("Справочник типов встреч")
    public void getMeetTypeId() {
        Response vocabulary = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/vocabulary/VocTaskMeetType")
                .andReturn();

        assertEquals(200, vocabulary.getStatusCode());

        meetTypeId = vocabulary.jsonPath()
                .getInt("find { it.name == 'Первичная встреча' }.id");
        assertNotNull(meetTypeId, "В справочнике должен быть тип встречи 'Первичная встреча'");
        assertTrue(meetTypeId > 0);
    }

    @Test
    @Order(16)
    @Description("Фиксация проведения первой встречи по сделке")
    @DisplayName("Проведение первой встречи")
    public void createFirstMeeting() {
        String body = String.format("{\"meetTypeId\":%d}", meetTypeId);

        Response createMeeting = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/deal/" + createdDealId + "/first-meeting/create")
                .andReturn();

        assertEquals(200, createMeeting.getStatusCode());
    }

    @Test
    @Order(17)
    @Description("Заполнение анкеты контакта перед сделкой")
    @DisplayName("Обновление данных контакта")
    public void updateContactProfile() {
        String body = String.format(
                "{\"vip\":false,\"media\":false,\"smsNotificationsDisabled\":false,\"responsibleUserId\":1," +
                        "\"name\":\"%s\",\"family\":\"%s\"," +
                        "\"contactsInfoList\":[{\"contactsInfoTypeId\":2,\"name\":\"%s\",\"isConfirm\":false}]," +
                        "\"waitingList\":[],\"pol\":\"MALE\",\"birthDate\":\"2001-09-08\",\"ageGroupId\":1," +
                        "\"familyComposition\":2,\"hasChildren\":false,\"regionOfLiving\":41," +
                        "\"sberEmployeeStatusId\":2,\"familyRelations\":[],\"hasCar\":false}",
                dynamicName, dynamicSurname, dynamicEmail
        );

        Response updateContact = RestAssured
                .given()
                .log().ifValidationFails()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/contact/" + createdContactId)
                .andReturn();

        assertEquals(200, updateContact.getStatusCode());
        assertEquals(createdContactId, updateContact.jsonPath().getInt("id"));
    }

    @Test
    @Order(18)
    @Description("Обновление аналитики по лиду (целевой/нецелевой)")
    @DisplayName("Аналитика лида")
    public void updateInterestAnalytic() {
        String body = "{\"isTarget\":true,\"informationNotProvided\":true}";

        Response updateAnalytic = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/interest/" + createdInterestId + "/analytic")
                .andReturn();

        assertEquals(200, updateAnalytic.getStatusCode());
    }

    @Test
    @Order(19)
    @Description("Заполнение анкеты интереса (форма объекта)")
    @DisplayName("Форма интереса")
    public void updateInterestForm() {
        String body = "{\"questionnaireType\":\"DEAL\",\"interestCategory\":1,\"interestingHousings\":[]," +
                "\"quartersOfInterests\":[],\"numberOfBedrooms\":[],\"designFeatures\":[],\"floors\":[]," +
                "\"informationNotProvided\":true}";

        Response updateForm = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .put("/api/v1/interest/" + createdInterestId + "/form")
                .andReturn();

        assertEquals(200, updateForm.getStatusCode());
    }

    @Test
    @Order(20)
    @Description("Подготовка сделки — перевод лида в статус 'Сделка'")
    @DisplayName("Подготовка сделки")
    public void prepareDeal() {
        Response prepare = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .post("/api/v1/deal/" + createdDealId + "/prepare")
                .andReturn();

        assertEquals(200, prepare.getStatusCode());
        assertEquals(createdDealId, prepare.jsonPath().getInt("id"));

        String leadStatus = prepare.jsonPath().getString("parentInterest.status.name");
        assertEquals("Сделка", leadStatus, "После подготовки сделки лид должен перейти в статус 'Сделка'");
    }

    @Test
    @Order(21)
    @Description("Проверка графика платежей по подготовленной сделке")
    @DisplayName("График платежей сделки")
    public void checkSchedulePayments() {
        Response schedule = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken)
                .get("/api/v1/deal/" + createdDealId + "/schedule_payments")
                .andReturn();

        assertEquals(200, schedule.getStatusCode());
        assertFalse(schedule.jsonPath().getBoolean("edited"));
        assertEquals(0, schedule.jsonPath().getDouble("totalAmount"));
    }
}