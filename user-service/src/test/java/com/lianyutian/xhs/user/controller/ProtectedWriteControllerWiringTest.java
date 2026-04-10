package com.lianyutian.xhs.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lianyutian.xhs.user.repository.mybatis.UserAddressMapper;
import com.lianyutian.xhs.user.service.address.AddressService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ProtectedWriteControllerWiringTest {

    @Test
    void shouldDependOnAddressServiceInsteadOfMapperForOwnershipLookup() {
        Field[] fields = ProtectedWriteController.class.getDeclaredFields();
        boolean hasAddressServiceField = false;
        boolean hasMapperField = false;
        for (Field field : fields) {
            if (field.getType() == AddressService.class) {
                hasAddressServiceField = true;
            }
            if (field.getType() == UserAddressMapper.class) {
                hasMapperField = true;
            }
        }
        assertThat(hasAddressServiceField).isTrue();
        assertThat(hasMapperField).isFalse();
    }
}
