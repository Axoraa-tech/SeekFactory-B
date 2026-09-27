package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;

import java.util.List;
import java.util.Map;

public interface ManufacturerService {

    List<ManufacturerResponse> listVerified(int limit);

    ManufacturerDetailResponse getBySlug(String slug, String viewerId);

    List<ManufacturerResponse> listAll();

    /** Follows or unfollows an approved factory. Returns { following, followerCount }. */
    Map<String, Object> toggleFollow(String manufacturerId, String userId);

    /** Approved factories the user follows, most recent first. */
    List<ManufacturerResponse> listFollowing(String userId);
}
