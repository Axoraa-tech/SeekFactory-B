package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;

import java.util.List;

public interface RfqService {

    RfqResponse submit(String userId, RfqCreateRequest request);

    List<RfqResponse> listByUser(String userId);
}