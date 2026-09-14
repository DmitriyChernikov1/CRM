import io.qameta.allure.Description;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LeadTest {

    private static final Logger log = LoggerFactory.getLogger(LeadTest.class);
    private static String accessToken;

    private static String dynamicEmail;
    private static String dynamicPhone;
    private static String dynamicName;
    private static String dynamicSurname;
    private static Integer createdInterestId;

    @BeforeAll
    static void setup() {

        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        // Отключаем проверку SSL-сертификата и hostname именно на уровне RestAssured,

        RestAssured.useRelaxedHTTPSValidation();

        accessToken = new AuthTokenTest().getAuthToken();

        // Генерация уникальных тестовых данных
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        dynamicEmail = "autotest." + uniqueSuffix + "@gmail.com";
        dynamicPhone = "+7" + (9000000000L + ThreadLocalRandom.current().nextLong(100000000L));
        dynamicName = "Дмитрий" + uniqueSuffix;
        dynamicSurname = "Черников" + uniqueSuffix;
    }

    @Test
    @Order(1)
    @Description("Поиск пользователя (ответственного) по id перед созданием лида")
    @DisplayName("Поиск пользователя по id")
    public void searchUserById() {
        Response searchUser = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .queryParam("property", "title")
                .queryParam("direction", "ASC")
                .queryParam("ids", 1)
                .get("/api/v1/user/search")
                .andReturn();

        int statusCode = searchUser.getStatusCode();
        assertEquals(200, statusCode);

        int totalCount = searchUser.jsonPath().getInt("totalCount");
        assertTrue(totalCount > 0, "Список пользователей не должен быть пустым");

        String userTitle = searchUser.jsonPath().getString("data[0].title");
        assertNotNull(userTitle, "Поле title у пользователя не должно быть null");
    }

    @Test
    @Order(2)
    @Description("Проверка отсутствия дублей лида по email/телефону перед созданием (данные заведомо уникальные)")
    @DisplayName("Проверка дублей перед созданием лида")
    public void checkDuplicatesBeforeCreate() {
        String body = String.format(
                "{\"phones\":[],\"emails\":[\"%s\"]}",
                dynamicEmail
        );

        Response duplicates = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();

        int statusCode = duplicates.getStatusCode();
        assertEquals(200, statusCode);

        int totalCount = duplicates.jsonPath().getInt("totalCount");
        assertEquals(0, totalCount, "Для нового уникального email дублей быть не должно");
    }

    @Test
    @Order(3)
    @Description("Создание нового лида (интереса) с динамическими контактными данными")
    @DisplayName("Создание лида")
    public void createInterest() {
        String body = buildInterestBody(dynamicName, dynamicSurname, dynamicEmail);

        Response createInterest = RestAssured
                .given()
                .log().all()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest")
                .andReturn();

        createInterest.prettyPrint();
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

        log.info("Создан лид с ID: {}", createdInterestId);
    }

    @Test
    @Order(4)
    @Description("Проверка появления дубля после создания лида с тем же email")
    @DisplayName("Проверка дублей после создания лида")
    public void checkDuplicatesAfterCreate() {
        assertNotNull(createdInterestId, "Лид должен быть создан в предыдущем тесте");

        String body = String.format(
                "{\"phones\":[],\"emails\":[\"%s\"]}",
                dynamicEmail
        );

        Response duplicates = RestAssured
                .given()
                .body(body)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();

        int statusCode = duplicates.getStatusCode();
        assertEquals(200, statusCode);

        int totalCount = duplicates.jsonPath().getInt("totalCount");
        assertTrue(totalCount >= 1, "После создания лида с этим email должен найтись как минимум один дубль");
    }

    @Test
    @Order(5)
    @Description("Получение информации по созданному лиду по ID и проверка соответствия отправленным данным")
    @DisplayName("Получение информации по лиду")
    public void getInterestInfo() {
        assertNotNull(createdInterestId, "Лид должен быть создан в предыдущем тесте");

        Response getInfo = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/" + createdInterestId)
                .andReturn();

        int statusCode = getInfo.getStatusCode();
        assertEquals(200, statusCode);

        int id = getInfo.jsonPath().getInt("id");
        assertEquals(createdInterestId, id);

        String surname = getInfo.jsonPath().getString("extContact.surname");
        assertEquals(dynamicSurname, surname, "Фамилия в ответе должна совпадать с отправленной");

        String crDate = getInfo.jsonPath().getString("crDate");
        assertNotNull(crDate, "Поле crDate не должно быть null");
        assertFalse(crDate.isEmpty(), "Поле crDate не должно быть пустым");
    }

    @Test
    @Order(6)
    @Description("Проверка возможности удаления лида (allow/delete)")
    @DisplayName("Проверка допустимости удаления лида")
    public void checkAllowDeleteInterest() {
        assertNotNull(createdInterestId, "Лид должен быть создан в предыдущем тесте");

        Response allowDelete = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/allow/delete/" + createdInterestId)
                .andReturn();

        int statusCode = allowDelete.getStatusCode();
        assertEquals(200, statusCode);

        // Эндпоинт возвращает "голое" число (id лида) в теле ответа, если удаление разрешено
        int returnedId = Integer.parseInt(allowDelete.getBody().asString().trim());
        assertEquals(createdInterestId, returnedId,
                "Эндпоинт allow/delete должен вернуть id лида, если удаление разрешено");
    }

    @Test
    @Order(7)
    @Description("Получение количества лайков по свежесозданному лиду (ожидается 0)")
    @DisplayName("Получение количества лайков лида")
    public void getInterestLikesCount() {
        assertNotNull(createdInterestId, "Лид должен быть создан в предыдущем тесте");

        Response likesCount = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/" + createdInterestId + "/likes_count")
                .andReturn();

        int statusCode = likesCount.getStatusCode();
        assertEquals(200, statusCode);

        int count = Integer.parseInt(likesCount.getBody().asString().trim());
        assertEquals(0, count, "У только что созданного лида не должно быть лайков");
    }

    @Test
    @Order(8)
    @Description("Получение ленты событий (feed) по созданному лиду")
    @DisplayName("Получение ленты событий лида")
    public void getInterestFeed() {
        assertNotNull(createdInterestId, "Лид должен быть создан в предыдущем тесте");

        Response feed = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/" + createdInterestId + "/feed")
                .andReturn();

        int statusCode = feed.getStatusCode();
        assertEquals(200, statusCode);

        assertNotNull(feed.jsonPath().get("opened"), "Секция opened должна присутствовать в ленте");
        assertNotNull(feed.jsonPath().get("closed"), "Секция closed должна присутствовать в ленте");
        assertNotNull(feed.jsonPath().get("canceled"), "Секция canceled должна присутствовать в ленте");
        assertNotNull(feed.jsonPath().get("notifications"), "Секция notifications должна присутствовать в ленте");
    }

    @Test
    @Order(9)
    @Description("Получение справочника подкатегорий источника информации о лиде")
    @DisplayName("Получение справочника источников информации")
    public void getInformationSourceSubCategoryVocabulary() {
        Response vocabulary = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/vocabulary/VocInformationSourceSubCategory")
                .andReturn();

        int statusCode = vocabulary.getStatusCode();
        assertEquals(200, statusCode);

        java.util.List<Object> items = vocabulary.jsonPath().getList("$");
        assertFalse(items.isEmpty(), "Справочник источников информации не должен быть пустым");
    }

    @Test
    @Order(10)
    @Description("Полный флоу: создание лида со случайными контактными данными, проверка через дубли и получение данных")
    @DisplayName("Сквозной сценарий создания и проверки лида")
    public void createInterestFullFlow() {
        // Собственные, независимые от остальных тестов данные — чтобы сценарий был самодостаточным
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "autotest.flow." + uniqueSuffix + "@gmail.com";
        String name = "Флоу" + uniqueSuffix;
        String surname = "Проверочный" + uniqueSuffix;

        // 1. Убеждаемся, что дублей нет
        String duplicatesBody = String.format("{\"phones\":[],\"emails\":[\"%s\"]}", email);
        Response duplicatesBefore = RestAssured
                .given()
                .body(duplicatesBody)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();
        assertEquals(200, duplicatesBefore.getStatusCode());
        assertEquals(0, duplicatesBefore.jsonPath().getInt("totalCount"));

        // 2. Создаём лид
        String createBody = buildInterestBody(name, surname, email);
        Response createResponse = RestAssured
                .given()
                .body(createBody)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest")
                .andReturn();
        assertEquals(200, createResponse.getStatusCode());

        Integer flowInterestId = createResponse.jsonPath().getInt("id");
        assertNotNull(flowInterestId);
        assertTrue(flowInterestId > 0);
        log.info("Флоу-тест: создан лид с ID {}", flowInterestId);

        // 3. Проверяем, что лид доступен по ID и данные совпадают
        Response getResponse = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/" + flowInterestId)
                .andReturn();
        assertEquals(200, getResponse.getStatusCode());
        assertEquals(name, getResponse.jsonPath().getString("extContact.name"));
        assertEquals(surname, getResponse.jsonPath().getString("extContact.surname"));

        // 4. Проверяем, что теперь дубль находится
        Response duplicatesAfter = RestAssured
                .given()
                .body(duplicatesBody)
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .post("/api/v1/interest/duplicates")
                .andReturn();
        assertEquals(200, duplicatesAfter.getStatusCode());
        assertTrue(duplicatesAfter.jsonPath().getInt("totalCount") >= 1);

        // 5. Проверяем, что удаление разрешено (доп. проверка целостности флоу)
        Response allowDelete = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .get("/api/v1/interest/allow/delete/" + flowInterestId)
                .andReturn();
        assertEquals(200, allowDelete.getStatusCode());
    }

    /**
     * Собирает JSON-тело запроса на создание лида (/api/v1/interest) с динамическими данными.
     * Структура тела восстановлена из HAR-файла (POST /api/v1/interest).
     */
    private static String buildInterestBody(String name, String surname, String email) {
        return String.format(
                "{\"responsible\":{\"responsibleType\":\"USER\",\"responsibleId\":1}," +
                        "\"extContact\":{\"name\":\"%s\",\"surname\":\"%s\"," +
                        "\"contactsInfoList\":[{\"contactsInfoTypeId\":2,\"name\":\"%s\",\"isConfirm\":false}]}}",
                name, surname, email
        );
    }


    }