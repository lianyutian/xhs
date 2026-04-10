package com.lianyutian.xhs.user.service.address;

import com.lianyutian.xhs.user.model.domain.UserAddress;
import java.util.List;

public interface AddressService {

    UserAddress create(Long ownerUserId, AddressCommand command);

    UserAddress update(Long ownerUserId, Long addressId, AddressCommand command);

    void delete(Long ownerUserId, Long addressId);

    List<UserAddress> list(Long ownerUserId);

    UserAddress requireOwnedAddress(Long ownerUserId, Long addressId);
}
