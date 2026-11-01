package com.atelier.media.service;

import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Local request-contract tests only: mocked SDK, no S3 objects, policies or credentials accessed. */
class S3PrivateSealTest {
    private static final String SOURCE="restaurant-atelier-private/cafe/branding/draft-upload.webp";
    private static final String DESTINATION="restaurant-atelier-private/cafe/branding/sealed-copy.webp";
    private static final String ETAG="\"synthetic-draft-etag\"";
    S3Client client;
    S3Storage storage;
    @BeforeEach void setup(){
        client=mock(S3Client.class);
        storage=new S3Storage(mock(S3Presigner.class),client,TestFixtures.awsProperties(),TestFixtures.atelierProperties());
        when(client.copyObject(any(CopyObjectRequest.class))).thenReturn(CopyObjectResponse.builder().build());
    }
    @Test void conditionalCopyUsesSameBucketAndPrivateNoStoreReplacementMetadataWithoutAcl(){
        storage.sealPrivateCopy(SOURCE,DESTINATION,ETAG,"image/webp");
        ArgumentCaptor<CopyObjectRequest> request=ArgumentCaptor.forClass(CopyObjectRequest.class);verify(client).copyObject(request.capture());
        var copy=request.getValue();
        assertThat(copy.bucket()).isEqualTo("atelier-test-bucket");
        assertThat(copy.key()).isEqualTo(DESTINATION);
        assertThat(copy.copySource()).isEqualTo("atelier-test-bucket/"+SOURCE);
        assertThat(copy.copySourceIfMatch()).isEqualTo(ETAG);
        assertThat(copy.metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
        assertThat(copy.cacheControl()).isEqualTo("private, no-store");
        assertThat(copy.contentType()).isEqualTo("image/webp");
        assertThat(copy.aclAsString()).isNull();
    }
    static Stream<Arguments> unsafeInputs(){
        return Stream.of(
                Arguments.of("restaurant-atelier/cafe/branding/public.webp",DESTINATION,ETAG),
                Arguments.of(SOURCE,"restaurant-atelier/cafe/branding/public.webp",ETAG),
                Arguments.of(SOURCE,SOURCE,ETAG),
                Arguments.of(SOURCE,DESTINATION,""),
                Arguments.of(SOURCE,DESTINATION,"   "),
                Arguments.of(SOURCE,DESTINATION,null),
                Arguments.of(null,DESTINATION,ETAG),
                Arguments.of(SOURCE,null,ETAG),
                Arguments.of("restaurant-atelier-private-deceptive/cafe/draft.webp",DESTINATION,ETAG),
                Arguments.of(SOURCE,"restaurant-atelier-private-deceptive/cafe/sealed.webp",ETAG));
    }
    @ParameterizedTest @MethodSource("unsafeInputs")
    void invalidCopyInputsAreRejectedBeforeAnySdkCall(String source,String destination,String etag){
        assertThatThrownBy(()->storage.sealPrivateCopy(source,destination,etag,"image/webp")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(client);
    }
    @Test void transportFailurePropagatesForCallerToRecordUnknownCleanupDisposition(){
        SdkClientException failure=SdkClientException.create("synthetic copy transport failure");
        when(client.copyObject(any(CopyObjectRequest.class))).thenThrow(failure);
        assertThatThrownBy(()->storage.sealPrivateCopy(SOURCE,DESTINATION,ETAG,"image/webp")).isSameAs(failure);
    }
    @Test void conditionalSourceFailurePropagatesRatherThanPretendingCopySucceeded(){
        var builder=S3Exception.builder();builder.statusCode(412);builder.message("synthetic source changed");S3Exception failure=(S3Exception)builder.build();
        when(client.copyObject(any(CopyObjectRequest.class))).thenThrow(failure);
        assertThatThrownBy(()->storage.sealPrivateCopy(SOURCE,DESTINATION,ETAG,"image/webp")).isSameAs(failure);
    }
}
