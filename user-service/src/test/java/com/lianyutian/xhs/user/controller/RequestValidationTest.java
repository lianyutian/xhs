package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.controller.request.AddressUpsertRequest;
import com.lianyutian.xhs.user.controller.request.AddressWriteRequest;
import com.lianyutian.xhs.user.controller.request.SendEmailCodeRequest;
import com.lianyutian.xhs.user.controller.request.UploadRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldValidateEmailCodeRequestTargetEmailFormat() {
        SendEmailCodeRequest request = new SendEmailCodeRequest("REGISTER", "not-an-email", null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldValidateAddressWriteRequestOwnerAndAddressId() {
        AddressWriteRequest request = new AddressWriteRequest(null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldValidateUploadRequestContentTypeAndSize() {
        UploadRequest request = new UploadRequest("", 0);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldRejectAddressUpsertWhenRequiredFieldsBlankOrMissing() {
        AddressUpsertRequest request = new AddressUpsertRequest(
            " ",
            "13800000000",
            " ",
            "",
            " ",
            " ",
            "310000",
            null
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldRejectAddressUpsertWhenPhoneIsInvalid() {
        AddressUpsertRequest request = new AddressUpsertRequest(
            "name",
            "12345678901",
            "zhejiang",
            "hangzhou",
            "xihu",
            "road 1",
            "310000",
            false
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldRejectAddressUpsertWhenLengthExceedsLimits() {
        AddressUpsertRequest request = new AddressUpsertRequest(
            "n".repeat(65),
            "13800000000",
            "p".repeat(65),
            "c".repeat(65),
            "d".repeat(65),
            "x".repeat(256),
            "z".repeat(33),
            false
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void shouldAllowAddressUpsertWhenPostalCodeIsNullOrLooseText() {
        AddressUpsertRequest nullPostalCode = new AddressUpsertRequest(
            "name",
            "13800000000",
            "zhejiang",
            "hangzhou",
            "xihu",
            "road 1",
            null,
            false
        );
        AddressUpsertRequest loosePostalCode = new AddressUpsertRequest(
            "name",
            "13800000000",
            "zhejiang",
            "hangzhou",
            "xihu",
            "road 1",
            "postal-code-text-310000",
            false
        );

        assertThat(validator.validate(nullPostalCode)).isEmpty();
        assertThat(validator.validate(loosePostalCode)).isEmpty();
    }
}
