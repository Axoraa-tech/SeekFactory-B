package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;

import java.util.List;

public interface ManufacturerService {

    List<ManufacturerResponse> listVerified(int limit);

    ManufacturerDetailResponse getBySlug(String slug);

    List<ManufacturerResponse> listAll();
}