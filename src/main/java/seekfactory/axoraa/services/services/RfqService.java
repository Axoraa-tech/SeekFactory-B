package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;

import java.util.List;

public interface RfqService {

    RfqResponse submit(String userId, RfqCreateRequest request);

    /** The buyer's RFQs, newest first, each with its quote count. */
    List<RfqResponse> listByUser(String userId);

    /** One of the buyer's RFQs with every factory quote on it. */
    RfqResponse getMine(String userId, String rfqId);

    RfqResponse cancel(String userId, String rfqId);

    /**
     * Accepts a quote: the RFQ becomes ACCEPTED, other pending quotes are rejected,
     * and an order is placed with the quoting factory using the given delivery details.
     */
    RfqResponse acceptQuote(String userId, String rfqId, String quoteId, OrderContactRequest contact);

    RfqResponse rejectQuote(String userId, String rfqId, String quoteId);
}
