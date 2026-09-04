import io.qameta.allure.Description;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;


import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OtherTest {

    private static String accessToken;

    @BeforeAll
    static void setup() {

        RestAssured.baseURI = "https://preprod-crm.sbercity.ru";
        // Отключаю проверку SSL-сертификата

        RestAssured.useRelaxedHTTPSValidation();

        accessToken = new AuthTokenTest().getAuthToken();}
    private static int id;
    private static int typeId;
    @Test
    @Order(1)
    @DisplayName("получение списка")
    @Description("получение списков")
    public void getList(){
        String body = "{\"page\":1,\"size\":100,\"sortBy\":[{\"property\":\"price\",\"direction\":\"ASC\"}],\"filter\":{\"price\":{},\"statusIds\":[1]}}";
        Response list = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .body(body)
                .post("/api/v1/real_estate/catalog/list")
                .andReturn();
        id = list.jsonPath().getInt("data.id[0]");

        typeId = list.jsonPath().getInt("data.type.id[0]");
        int statusCode = list.statusCode();
        assertEquals(200,statusCode);

        }
    @Test
    @Order(2)
    @DisplayName("изменение цены")
    @Description("изменение цены")
public void update(){
        String body1 = "{\"typeId\":" + typeId + ",\"id\":" + id + ",\"statusId\":1,\"futurePrice\":100000}";

        Response update = RestAssured
                .given()
                .headers("Authorization", "Bearer " + accessToken, "Content-Type", "application/json; charset=UTF-8")
                .body(body1)
                .post("/api/v1/real_estate/catalog/update")
                .andReturn();

        int statusCode = update.statusCode();
        assertEquals(200,statusCode);
    }
    }

