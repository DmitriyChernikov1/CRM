import io.qameta.allure.Description;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class AuthTest {
    private static final String BASE_URL = "https://preprod-crm.sbercity.ru";

    static {
        // Отключаем проверку SSL сертификатов
        disableSslVerification();
    }

    private static void disableSslVerification() {
        try {
            // Создаем доверительный менеджер, который доверяет всем сертификатам
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Отключаем проверку имени хоста
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) { return true; }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RequestSpecification authRequest() {
        return given()
                .baseUri(BASE_URL)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("accept", "application/json")
                .header("Accept-Language", "ru-RU")
                .relaxedHTTPSValidation(); // Дополнительно для RestAssured
    }

    @Test
    @Description("авторизация по валидным данным")
    @DisplayName("Авторизация по валидным данным ")
    public void auth() {
        Map<String, String> authBody = new HashMap<>();
        authBody.put("email", "admin@admin.ru");
        authBody.put("password", "AdvddUawVeX~FGKJ11Icw}@G#W");

        Response auth = authRequest()
                .body(authBody)
                .post("/api/v1/auth/signin")
                .then()
                .statusCode(200)
                .extract().response();
        String token2 = auth.path("accessToken");
        auth.prettyPrint();
    }
}