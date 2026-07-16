package io.github.yamlmapper.benchmark.mapper;

import com.google.cloud.retail.v2.UserEvent;
import com.google.cloud.retail.v2.UserInfo;
import io.github.yamlmapper.benchmark.dto.UserEventDto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for UserEvent benchmark comparison.
 * Simple field mapping, no transforms - fair comparison with other approaches.
 */
@Mapper
public interface UserEventMapStructMapper {

    UserEventMapStructMapper INSTANCE = Mappers.getMapper(UserEventMapStructMapper.class);

    @Mapping(target = "userInfo", ignore = true)
    @Mapping(target = "pageCategoriesList", ignore = true)
    @Mapping(target = "unknownFields", ignore = true)
    @Mapping(target = "productDetailsList", ignore = true)
    @Mapping(target = "completionDetail", ignore = true)
    @Mapping(target = "attributesMap", ignore = true)
    @Mapping(target = "cartId", ignore = true)
    @Mapping(target = "purchaseTransaction", ignore = true)
    @Mapping(target = "filter", ignore = true)
    @Mapping(target = "orderBy", ignore = true)
    @Mapping(target = "offset", ignore = true)
    @Mapping(target = "pageViewId", ignore = true)
    @Mapping(target = "entity", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "eventTime", ignore = true)
    @Mapping(target = "experimentIdsList", ignore = true)
    @Mapping(target = "attributionToken", ignore = true)
    UserEvent toProtobuf(UserEventDto dto);

    @AfterMapping
    default void afterMapping(UserEventDto dto, @MappingTarget UserEvent.Builder builder) {
        // Handle userInfo
        if (dto.getUserInfo() != null) {
            UserInfo.Builder userInfoBuilder = UserInfo.newBuilder();
            if (dto.getUserInfo().getUserId() != null) {
                userInfoBuilder.setUserId(dto.getUserInfo().getUserId());
            }
            if (dto.getUserInfo().getIpAddress() != null) {
                userInfoBuilder.setIpAddress(dto.getUserInfo().getIpAddress());
            }
            builder.setUserInfo(userInfoBuilder.build());
        }

        // Handle pageCategories
        if (dto.getPageCategories() != null) {
            builder.addAllPageCategories(dto.getPageCategories());
        }
    }
}
