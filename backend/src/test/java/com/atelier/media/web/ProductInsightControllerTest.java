package com.atelier.media.web;

import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.security.*;
import com.atelier.media.service.*;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP/security and payload contracts only. Tenant business/account decisions remain in real service integration tests. */
@WebMvcTest({ProductInsightsController.class,BrandDraftAssetController.class})
@Import({SecurityConfig.class,TokenService.class,AdminAccess.class})
@ActiveProfiles("test")
class ProductInsightControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GuestInsightService insights;
    @MockBean BrandDraftAssets assets;
    @MockBean ClientIpResolver ips;
    @MockBean PlatformUserRepository users;
    PlatformUser admin;
    private static final String EVENT_ID="bfbcc904-71b6-4300-97cc-c7a51f47e54f";
    private static final String EVENT_BODY="{\"slug\":\"cafe\",\"eventId\":\""+EVENT_ID+"\",\"eventType\":\"MENU_VISIT\",\"dishId\":null,\"tableToken\":null,\"category\":null}";
    private static final String ASSET_BODY="{\"filename\":\"synthetic-logo.webp\",\"contentType\":\"image/webp\",\"uploadSizeBytes\":500}";
    @BeforeEach void seed(){
        admin=PlatformUser.builder().id(1L).email("insights-operator@platform.test").displayName("Insights operator").passwordHash("synthetic-test-placeholder").mustChangePassword(false).build();
        when(users.findById(1L)).thenReturn(Optional.of(admin));when(ips.resolveClientIp(any())).thenReturn("198.51.100.25");
    }
    @Test void anonymousV2ObservationUsesResolvedIpAndAcknowledgesNoContentWithoutCaching() throws Exception {
        mvc.perform(post("/api/v1/public/insights/event").contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(""));
        verify(insights).record(argThat(dto->dto.slug().equals("cafe")&&dto.eventId().equals(EVENT_ID)&&dto.eventType().equals("MENU_VISIT")&&dto.tableToken()==null),eq("198.51.100.25"));
    }
    @Test void failedV2ValidationIsNotAcknowledgedAsAccepted() throws Exception {
        doThrow(new InvalidFieldException("eventType","Unsupported client observation.")).when(insights).record(any(),anyString());
        mvc.perform(post("/api/v1/public/insights/event").contentType(MediaType.APPLICATION_JSON).content(EVENT_BODY))
                .andExpect(status().isBadRequest());
    }
    @Test void malformedObservationJsonNeverReachesIngestion() throws Exception {
        mvc.perform(post("/api/v1/public/insights/event").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());verifyNoInteractions(insights);
    }
    @Test void ownerReportUsesAuthenticatedTenantAndExplicitUtcDateRange() throws Exception {
        LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,31);
        when(insights.report(any(),eq(from),eq(to))).thenReturn(Map.of("zone","UTC","events",List.of()));
        mvc.perform(get("/api/v1/studio/insights?from=2026-10-01&to=2026-10-31&restaurantId=999").with(TestFixtures.staff(7,"cafe")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.zone").value("UTC"));
        verify(insights).report(argThat(t->t.restaurantId()==7&&t.slug().equals("cafe")),eq(from),eq(to));
    }
    @Test void invalidDateDoesNotReachReportService() throws Exception {
        mvc.perform(get("/api/v1/studio/insights?from=not-a-date&to=2026-10-31").with(TestFixtures.staff(7,"cafe")))
                .andExpect(status().isBadRequest());verifyNoInteractions(insights);
    }
    @ParameterizedTest @CsvSource({
            "GET,/api/v1/studio/insights",
            "GET,/api/v1/admin/insights",
            "POST,/api/v1/studio/appearance/assets/LOGO/presign",
            "POST,/api/v1/studio/appearance/assets/synthetic-asset/confirm"})
    void protectedReportsAndDraftAssetRoutesRejectAnonymous(String method,String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(insights,assets);
    }
    @Test void currentAdministratorReceivesPlatformAggregateForExplicitDates() throws Exception {
        LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,31);
        when(insights.platform(from,to)).thenReturn(Map.of("zone","UTC","events",List.of()));
        mvc.perform(get("/api/v1/admin/insights?from=2026-10-01&to=2026-10-31").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.zone").value("UTC"));
        verify(insights).platform(from,to);
    }
    @Test void revokedAdministratorCannotReadPlatformReport() throws Exception {
        admin.incrementTokenVersion();
        mvc.perform(get("/api/v1/admin/insights").with(TestFixtures.superAdmin()))
                .andExpect(status().isUnauthorized());verifyNoInteractions(insights);
    }
    @Test void restaurantSessionCannotReadPlatformInsights() throws Exception {
        mvc.perform(get("/api/v1/admin/insights").with(TestFixtures.staff(7,"cafe")))
                .andExpect(status().isForbidden());verifyNoInteractions(insights);
    }
    @Test void draftAssetPresignBindsOwnerPurposeAndSmallMetadataOnly() throws Exception {
        when(assets.issue(any(),eq("LOGO"),any())).thenReturn(Map.of("id","synthetic-asset","uploadUrl","https://example.test/synthetic-upload"));
        mvc.perform(post("/api/v1/studio/appearance/assets/LOGO/presign?restaurantId=999").with(TestFixtures.staff(7,"cafe"))
                        .contentType(MediaType.APPLICATION_JSON).content(ASSET_BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").value("synthetic-asset"));
        verify(assets).issue(argThat(t->t.restaurantId()==7),eq("LOGO"),argThat(dto->dto.filename().equals("synthetic-logo.webp")&&dto.contentType().equals("image/webp")&&dto.uploadSizeBytes()==500L));
    }
    @Test void draftAssetConfirmationUsesAuthenticatedOwnerAndIssuedId() throws Exception {
        when(assets.verify(any(),eq("synthetic-asset"))).thenReturn(Map.of("id","synthetic-asset","purpose","LOGO"));
        mvc.perform(post("/api/v1/studio/appearance/assets/synthetic-asset/confirm?restaurantId=999").with(TestFixtures.staff(7,"cafe")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.purpose").value("LOGO"));
        verify(assets).verify(argThat(t->t.restaurantId()==7),eq("synthetic-asset"));
    }
    @Test void invalidAssetSizeIsRejectedBeforeIssuance() throws Exception {
        mvc.perform(post("/api/v1/studio/appearance/assets/LOGO/presign").with(TestFixtures.staff(7,"cafe"))
                        .contentType(MediaType.APPLICATION_JSON).content(ASSET_BODY.replace(":500",":0")))
                .andExpect(status().isBadRequest());verifyNoInteractions(assets);
    }
}
