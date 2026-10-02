package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Request.message.StartConversationRequest;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;

import seekfactory.axoraa.dto.Response.message.AttachmentUploadResponse;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConversationService {

    List<ConversationResponse> listRecent(String userId, int limit);

    /** Unread messages across every conversation of the user (buyer or factory side). */
    long unreadCount(String userId);

    ConversationResponse getOrCreateConversation(String userId, StartConversationRequest request);

    List<MessageResponse> getMessages(String conversationId, String userId);

    MessageResponse sendMessage(String conversationId, String userId, MessageSendRequest request);

    void markAsRead(String conversationId, String userId);

    /** Seller opens (or reuses) the chat with the buyer of one of their orders. */
    ConversationResponse openForOrder(String supplierUserId, String orderId);

    /** Seller opens (or reuses) the chat with the buyer who posted an RFQ. */
    ConversationResponse openForRfq(String supplierUserId, String rfqId);

    /** Orders between this conversation's buyer and factory (context picker). */
    List<OrderResponse> listConversationOrders(String conversationId, String userId);

    /** Upload an image/PDF for this conversation (participants only). */
    AttachmentUploadResponse uploadAttachment(String conversationId, String userId, MultipartFile file);

    /** Read an attachment (participants only). */
    Resource loadAttachment(String conversationId, String userId, String key);

    /** Throws unless the user is the buyer or the factory in this conversation. */
    void assertParticipant(String conversationId, String userId);
}