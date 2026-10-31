package com.atelier.media.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Static template contracts only: not an AWS IAM evaluator or deployed-access test. */
class S3PolicyTemplateTest {
    private static final String BUCKET = "arn:aws:s3:::YOUR_BUCKET_NAME";
    private static final List<String> ROOTS = List.of("restaurant-atelier/", "restaurant-atelier-private/");

    private JsonNode policy(String name) throws Exception {
        return new ObjectMapper().readTree(Path.of("../infra", name).toFile());
    }

    @Test
    void bothTemplatesParseAsPolicyDocuments() throws Exception {
        for (String name : List.of("iam-policy.json", "s3-public-read-policy.json")) {
            JsonNode document = policy(name);
            assertThat(document.path("Version").asText()).isEqualTo("2012-10-17");
            assertThat(document.path("Statement").isArray()).isTrue();
            assertThat(document.path("Statement").size()).isPositive();
        }
    }

    @Test
    void anonymousAllowIsOnlyPublicPrefixGet() throws Exception {
        JsonNode statements = policy("s3-public-read-policy.json").path("Statement");
        assertThat(statements.size()).isEqualTo(1);
        JsonNode grant = statements.get(0);
        assertThat(grant.path("Effect").asText()).isEqualTo("Allow");
        assertThat(grant.path("Principal").asText()).isEqualTo("*");
        assertThat(grant.path("Action").asText()).isEqualTo("s3:GetObject");
        assertThat(grant.path("Resource").asText()).isEqualTo(BUCKET + "/restaurant-atelier/*");
        assertThat(grant.has("NotResource")).isFalse();
        assertThat(grant.has("NotAction")).isFalse();
    }

    @Test
    void noBlanketPrivateReadDenyBlocksApplicationSigner() throws Exception {
        for (JsonNode statement : policy("s3-public-read-policy.json").path("Statement")) {
            assertThat(statement.path("Effect").asText()).isNotEqualTo("Deny");
        }
    }

    @Test
    void applicationObjectActionsAreLimitedToManagedPublicAndPrivatePrefixes() throws Exception {
        JsonNode statements = policy("iam-policy.json").path("Statement");
        assertThat(statements.size()).isEqualTo(2);
        JsonNode objects = statements.get(0);
        assertThat(objects.path("Effect").asText()).isEqualTo("Allow");
        assertThat(objects.path("Action")).isEqualTo(new ObjectMapper().valueToTree(
                List.of("s3:PutObject", "s3:GetObject", "s3:DeleteObject")));
        assertThat(objects.path("Resource")).isEqualTo(new ObjectMapper().valueToTree(
                ROOTS.stream().map(root -> BUCKET + "/" + root + "*").toList()));
        assertThat(objects.has("NotAction")).isFalse();
        assertThat(objects.has("NotResource")).isFalse();
    }

    @Test
    void listingUsesBucketArnWithExplicitPrefixCondition() throws Exception {
        JsonNode listing = policy("iam-policy.json").path("Statement").get(1);
        assertThat(listing.path("Effect").asText()).isEqualTo("Allow");
        assertThat(listing.path("Action").asText()).isEqualTo("s3:ListBucket");
        assertThat(listing.path("Resource").asText()).isEqualTo(BUCKET);
        assertThat(listing.path("Condition").path("StringLike").path("s3:prefix"))
                .isEqualTo(new ObjectMapper().valueToTree(ROOTS.stream().map(root -> root + "*").toList()));
        assertThat(listing.has("NotAction")).isFalse();
        assertThat(listing.has("NotResource")).isFalse();
    }

    @Test
    void objectResourcePatternsDoNotCoverUnrelatedOrLookalikePrefixes() throws Exception {
        for (JsonNode resource : policy("iam-policy.json").path("Statement").get(0).path("Resource")) {
            String pattern = resource.asText();
            assertThat(pattern).endsWith("/*");
            String literalPrefix = pattern.substring(0, pattern.length() - 1);
            for (String key : List.of("unrelated/file", "restaurant-atelier-evil/file", "restaurant-atelier-private-evil/file")) {
                assertThat((BUCKET + "/" + key).startsWith(literalPrefix)).isFalse();
            }
        }
    }
}
