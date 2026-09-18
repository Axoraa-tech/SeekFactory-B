package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.entity.Messages.Conversation;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.repository.Messages.ConversationRepository;
import seekfactory.axoraa.services.services.ConversationService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages B2B buyer-supplier chat conversations.
 *
 * The listRecent method returns conversations ordered by last message time,
 * enriched with the manufacturer info so the frontend can render
 * factory avatars, names, and verification badges in the chat list.
 *
 * This matches the frontend MessageRepository.listRecent() contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ConversationResponse> listRecent(String userId, int limit) {
        List<Conversation> conversations = conversationRepository
                .findByBuyerIdOrderByLastMessageAtDesc(userId);

        // Apply limit
        if (limit > 0 && conversations.size() > limit) {
            conversations = conversations.subList(0, limit);
        }

        return conversations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ConversationResponse mapToResponse(Conversation conversation) {
        Manufacturer manufacturer = conversation.getManufacturer();

        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        return ConversationResponse.builder()
                .id(conversation.getId())
                .manufacturerId(manufacturer.getId())
                .lastMessage(conversation.getLastMessageText())
                .lastMessageAt(conversation.getLastMessageAt() != null
                        ? conversation.getLastMessageAt().toString() : null)
                .unreadCount(conversation.getUnreadCountBuyer())
                .manufacturer(mfgResponse)
                .build();
    }
}