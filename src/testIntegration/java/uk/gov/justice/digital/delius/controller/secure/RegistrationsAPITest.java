package uk.gov.justice.digital.delius.controller.secure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class RegistrationsAPITest extends IntegrationTestBase {
    private static final String NOMS_NUMBER = "G9542VP";
    private static final String OFFENDER_ID = "2500343964";
    private static final String CRN = "X320741";

    @Test
    public void mustHaveCommunityRole() {
        final var token = createJwt("ROLE_BANANAS");

        given()
                .auth().oauth2(token)
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get("/offenders/offenderId/{offenderId}/registrations", OFFENDER_ID)
                .then()
                .statusCode(403);
    }

    @Test
    public void canGetRegistrationByOffenderId() {
        given()
                .auth().oauth2(tokenWithRoleCommunity())
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get("/offenders/offenderId/{offenderId}/registrations", OFFENDER_ID)
                .then()
                .statusCode(200)
                .body("registrations[2].register.description", is("Public Protection"))
                .body("registrations[2].startDate", is("2019-10-11"))
                .body("registrations[2].deregisteringNotes", nullValue())
                .body("registrations[3].deregisteringNotes", is("Ok again now"));

    }

    @Test
    public void canGetRegistrationByNOMSNumber() {
        given()
                .auth().oauth2(tokenWithRoleCommunity())
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get("/offenders/nomsNumber/{nomsNumber}/registrations", NOMS_NUMBER)
                .then()
                .statusCode(200)
                .body("registrations[2].register.description", is("Public Protection"))
                .body("registrations[2].deregisteringNotes", nullValue())
                .body("registrations[3].deregisteringNotes", is("Ok again now"))
                .body("registrations[3].numberOfPreviousDeregistrations", is(2));
    }

    @Test
    public void canGetRegistrationByCRN() {
        given()
                .auth().oauth2(tokenWithRoleCommunity())
                .contentType(APPLICATION_JSON_VALUE)
                .when()
                .get("/offenders/crn/{crn}/registrations", CRN)
                .then()
                .statusCode(200)
                .body("registrations[2].register.description", is("Public Protection"))
                .body("registrations[2].deregisteringNotes", nullValue())
                .body("registrations[3].deregisteringNotes", is("Ok again now"));
    }

    @Test
    public void canGetActiveRegistrationByCRN() {
        given()
            .auth().oauth2(tokenWithRoleCommunity())
            .contentType(APPLICATION_JSON_VALUE)
            .when()
            .get("/offenders/crn/{crn}/registrations?activeOnly=true", CRN)
            .then()
            .statusCode(200).body("registrations", hasSize(2));
    }

    @Nested
    @DisplayName("When multiple records match the same noms number")
    class DuplicateNOMSNumbers{
        @Nested
        @DisplayName("When only one of the records is current")
        class OnlyOneActive{
            @Test
            @DisplayName("will return the active record")
            void willReturnTheActiveRecord() {
                given()
                    .auth()
                    .oauth2(tokenWithRoleCommunity())
                    .contentType(APPLICATION_JSON_VALUE)
                    .when()
                    .get("/offenders/nomsNumber/G3232DD/registrations")
                    .then()
                    .statusCode(200);
            }
            @Test
            @DisplayName("will return a conflict response when fail on duplicate is set to true")
            void willReturnAConflictResponseWhenFailureOnDuplicate() {
                given()
                    .auth()
                    .oauth2(tokenWithRoleCommunity())
                    .contentType(APPLICATION_JSON_VALUE)
                    .when()
                    .get("/offenders/nomsNumber/G3232DD/registrations?failOnDuplicate=true")
                    .then()
                    .statusCode(409);
            }

        }
        @Nested
        @DisplayName("When both records have the same active state")
        class BothActive{
            @Test
            @DisplayName("will return a conflict response")
            void willReturnAConflictResponse() {
                given()
                    .auth()
                    .oauth2(tokenWithRoleCommunity())
                    .contentType(APPLICATION_JSON_VALUE)
                    .when()
                    .get("/offenders/nomsNumber/G3636DD/registrations")
                    .then()
                    .statusCode(409);
            }
        }
    }
}
