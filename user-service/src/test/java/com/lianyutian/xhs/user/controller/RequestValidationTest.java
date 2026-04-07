package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

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
}
