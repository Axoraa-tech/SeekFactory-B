package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Request.message.StartConversationRequest;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Messages.Conversation;
import seekfactory.axoraa.entity.Messages.Message;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.SenderType;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.Messages.ConversationRepository;
import seekfactory.axoraa.repository.Messages.MessageRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.services.services.SseService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages B2B buyer-supplier chat conversations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final SseService sseService;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listRecent(String userId, int limit) {
        Optional<Manufacturer> mfgOpt = manufacturerRepository.findByUserId(userId);

        List<Conversation> conversations;
        boolean isSupplier = mfgOpt.isPresent();

        if (isSupplier) {
            conversations = conversationRepository
                    .findByManufacturerIdOrderByLastMessageAtDesc(mfgOpt.get().getId());
        } else {
            conversations = conversationRepository
                    .findByBuyerIdOrderByLastMessageAtDesc(userId);
        }

        if (limit > 0 && conversations.size() > limit) {
            conversations = conversations.subList(0, limit);
        }

        return conversations.stream()
                .map(c -> mapToResponse(c, isSupplier))
                .collect(Collectors.toList());
    }

    @Override
    public ConversationResponse getOrCreateConversation(String userId, StartConversationRequest request) {
        User buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Manufacturer manufacturer = manufacturerRepository.findById(request.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", request.getManufacturerId()));

        Optional<Conversation> existing = conversationRepository.findByBuyerIdAndManufacturerId(userId, manufacturer.getId());
        Conversation conversation;

        if (existing.isPresent()) {
            conversation = existing.get();
        } else {
            conversation = Conversation.builder()
                    .buyer(buyer)
                    .manufacturer(manufacturer)
                    .unreadCountBuyer(0)
                    .unreadCountSupplier(0)
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        if (request.getInitialMessage() != null && !request.getInitialMessage().isBlank()) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .sender(buyer)
                    .senderType(SenderType.USER)
                    .messageText(request.getInitialMessage().trim())
                    .isRead(false)
                    .build();
            messageRepository.save(message);

            conversation.setLastMessageText(message.getMessageText());
            conversation.setLastMessageAt(OffsetDateTime.now());
            conversation.setUnreadCountSupplier(conversation.getUnreadCountSupplier() + 1);
            conversation = conversationRepository.save(conversation);
        }

        return mapToResponse(conversation, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        validateParticipant(conversation, userId);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MessageResponse sendMessage(String conversationId, String userId, MessageSendRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        SenderType senderType;
        if (conversation.getBuyer().getId().equals(userId)) {
            senderType = SenderType.USER;
            conversation.setUnreadCountSupplier(conversation.getUnreadCountSupplier() + 1);
        } else if (conversation.getManufacturer().getUser() != null &&
                conversation.getManufacturer().getUser().getId().equals(userId)) {
            senderType = SenderType.FACTORY;
            conversation.setUnreadCountBuyer(conversation.getUnreadCountBuyer() + 1);
        } else {
            throw new ForbiddenException("You are not a participant of this conversation");
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(user)
                .senderType(senderType)
                .messageText(request.getMessageText().trim())
                .attachmentName(request.getAttachmentName())
                .attachmentSize(request.getAttachmentSize())
                .attachmentUrl(request.getAttachmentUrl())
                .isRead(false)
                .build();

        message = messageRepository.save(message);

        conversation.setLastMessageText(message.getMessageText());
        conversation.setLastMessageAt(OffsetDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapToMessageResponse(message);
        sseService.pushMessageToConversation(conversationId, response);
        return response;
    }

    @Override
    public void markAsRead(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        validateParticipant(conversation, userId);

        if (conversation.getBuyer().getId().equals(userId)) {
            conversation.setUnreadCountBuyer(0);
        } else {
            conversation.setUnreadCountSupplier(0);
        }

        conversationRepository.save(conversation);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        for (Message msg : messages) {
            if (!msg.getSender().getId().equals(userId) && Boolean.FALSE.equals(msg.getIsRead())) {
                msg.setIsRead(true);
                messageRepository.save(msg);
            }
        }
    }

    // ─── Private Helpers ──────────────────────────────────────

    private void validateParticipant(Conversation conversation, String userId) {
        boolean isBuyer = conversation.getBuyer().getId().equals(userId);
        boolean isSupplier = conversation.getManufacturer().getUser() != null &&
                conversation.getManufacturer().getUser().getId().equals(userId);

        if (!isBuyer && !isSupplier) {
            throw new ForbiddenException("You are not authorized to access this conversation");
        }
    }

    private ConversationResponse mapToResponse(Conversation conversation, boolean isSupplierView) {
        Manufacturer manufacturer = conversation.getManufacturer();

        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        int unread = isSupplierView ? conversation.getUnreadCountSupplier() : conversation.getUnreadCountBuyer();
        
        String buyerCompany = conversation.getBuyer().getCompanyName();
        if (buyerCompany == null || buyerCompany.trim().isEmpty()) {
            buyerCompany = "Global Buyer";
        }

        return ConversationResponse.builder()
                .id(conversation.getId())
                .manufacturerId(manufacturer.getId())
                .buyerId(conversation.getBuyer().getId())
                .buyerName(conversation.getBuyer().getName())
                .buyerCompany(buyerCompany)
                .buyerAvatarUrl(conversation.getBuyer().getAvatarUrl())
                .lastMessage(conversation.getLastMessageText())
                .lastMessageAt(conversation.getLastMessageAt() != null
                        ? conversation.getLastMessageAt().toString() : null)
                .unreadCount(unread)
                .manufacturer(mfgResponse)
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderType(message.getSenderType().name())
                .senderName(message.getSender().getName())
                .senderAvatarUrl(message.getSender().getAvatarUrl())
                .messageText(message.getMessageText())
                .attachmentName(message.getAttachmentName())
                .attachmentSize(message.getAttachmentSize())
                .attachmentUrl(message.getAttachmentUrl())
                .isRead(Boolean.TRUE.equals(message.getIsRead()))
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : OffsetDateTime.now().toString())
                .build();
    }
}
