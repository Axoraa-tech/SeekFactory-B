package seekfactory.axoraa.services.serviceImpl;

import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Messages.Conversation;
import seekfactory.axoraa.entity.Messages.Message;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.OrderStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.Messages.ConversationRepository;
import seekfactory.axoraa.repository.Messages.MessageRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.OrderService;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.services.services.SseService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private SseService sseService;
    @Mock private OrderRequestRepository orderRequestRepository;
    @Mock private OrderService orderService;
    @Mock private MediaStorageService mediaStorageService;
    @Mock private NotificationService notificationService;
    @Mock private PresenceService presenceService;

    @InjectMocks private ConversationServiceImpl service;

    private User buyer;
    private User supplierUser;
    private Manufacturer factory;
    private Conversation conversation;
    private OrderRequest order;

    @BeforeEach
    void setUp() {
        buyer = User.builder().name("Arjun").build();
        buyer.setId("u-buyer");
        supplierUser = User.builder().name("Chen").build();
        supplierUser.setId("u-supplier");
        factory = Manufacturer.builder().name("Dongguan").user(supplierUser).build();
        factory.setId("mfr-1");
        conversation = Conversation.builder().buyer(buyer).manufacturer(factory).unreadCountBuyer(0).unreadCountSupplier(0).build();
        conversation.setId("c-1");
        order = OrderRequest.builder().referenceNumber("ORD-2026-AAAAAA").buyer(buyer).manufacturer(factory)
                .productName("Housing").quantity(10).unit("Set").status(OrderStatus.PENDING).build();
        order.setId("o-1");

        lenient().when(conversationRepository.findById("c-1")).thenReturn(Optional.of(conversation));
        lenient().when(userRepository.findById("u-supplier")).thenReturn(Optional.of(supplierUser));
        lenient().when(userRepository.findById("u-buyer")).thenReturn(Optional.of(buyer));
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId("m-1");
            m.setCreatedAt(Instant.now());
            return m;
        });
        lenient().when(modelMapper.map(any(), eq(ManufacturerResponse.class))).thenReturn(new ManufacturerResponse());
    }

    @Test
    void sellerSendsMessageWithOrderContextAndPdf() {
        when(orderRequestRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(mediaStorageService.contentTypeOf("11111111-1111-1111-1111-111111111111.pdf")).thenReturn("application/pdf");

        MessageSendRequest req = MessageSendRequest.builder()
                .messageText("About your order")
                .orderId("o-1")
                .attachmentUrl("/api/v1/conversations/c-1/attachments/11111111-1111-1111-1111-111111111111.pdf")
                .attachmentName("quote.pdf")
                .attachmentSize("120 KB")
                .build();
        MessageResponse res = service.sendMessage("c-1", "u-supplier", req);

        assertThat(res.getSenderType()).isEqualTo("FACTORY");
        assertThat(res.getOrder().getReferenceNumber()).isEqualTo("ORD-2026-AAAAAA");
        assertThat(res.getAttachmentContentType()).isEqualTo("application/pdf");
        assertThat(conversation.getUnreadCountBuyer()).isEqualTo(1);
        verify(sseService).pushMessageToConversation(eq("c-1"), any());
    }

    @Test
    void attachmentOnlyMessageIsAllowedAndSummarised() {
        when(mediaStorageService.contentTypeOf(any())).thenReturn("image/png");
        MessageSendRequest req = MessageSendRequest.builder()
                .attachmentUrl("/api/v1/conversations/c-1/attachments/11111111-1111-1111-1111-111111111111.png")
                .attachmentName("drawing.png").build();
        service.sendMessage("c-1", "u-buyer", req);
        assertThat(conversation.getLastMessageText()).endsWith("drawing.png");
    }

    @Test
    void emptyMessageIsRejected() {
        assertThatThrownBy(() -> service.sendMessage("c-1", "u-buyer", MessageSendRequest.builder().messageText("  ").build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void attachmentFromAnotherConversationIsRejected() {
        MessageSendRequest req = MessageSendRequest.builder().messageText("x")
                .attachmentUrl("/api/v1/conversations/c-OTHER/attachments/11111111-1111-1111-1111-111111111111.pdf").build();
        assertThatThrownBy(() -> service.sendMessage("c-1", "u-buyer", req)).isInstanceOf(BadRequestException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void orderFromAnotherBuyerCannotBeUsedAsContext() {
        User stranger = User.builder().name("Other").build();
        stranger.setId("u-other");
        OrderRequest foreign = OrderRequest.builder().buyer(stranger).manufacturer(factory).productName("x").quantity(1).build();
        when(orderRequestRepository.findById("o-foreign")).thenReturn(Optional.of(foreign));
        MessageSendRequest req = MessageSendRequest.builder().messageText("x").orderId("o-foreign").build();
        assertThatThrownBy(() -> service.sendMessage("c-1", "u-supplier", req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nonParticipantCannotStreamOrSend() {
        assertThatThrownBy(() -> service.assertParticipant("c-1", "u-intruder")).isInstanceOf(ForbiddenException.class);
        when(userRepository.findById("u-intruder")).thenReturn(Optional.of(User.builder().name("X").build()));
        assertThatThrownBy(() -> service.sendMessage("c-1", "u-intruder", MessageSendRequest.builder().messageText("hi").build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonParticipantCannotOpenAnAttachment() {
        // Rejected before storage is touched, so no file and no signed link is ever produced
        assertThatThrownBy(() -> service.loadAttachment("c-1", "u-intruder", "11111111-1111-1111-1111-111111111111.pdf"))
                .isInstanceOf(ForbiddenException.class);
        verify(mediaStorageService, never()).loadPrivate(any(), any());
        verify(mediaStorageService, never()).privateUrl(any(), any());
    }

    @Test
    void sellerOpensChatFromOrderReusingExistingConversation() {
        when(orderRequestRepository.findById("o-1")).thenReturn(Optional.of(order));
        when(conversationRepository.findByBuyerIdAndManufacturerId("u-buyer", "mfr-1")).thenReturn(Optional.of(conversation));
        ConversationResponse res = service.openForOrder("u-supplier", "o-1");
        assertThat(res.getId()).isEqualTo("c-1");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void anotherFactoryCannotOpenChatForTheOrder() {
        when(orderRequestRepository.findById("o-1")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.openForOrder("u-other-supplier", "o-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void messageToSomeoneWatchingTheChatIsReadLiveWithoutAlert() {
        when(sseService.isWatching("c-1", "u-buyer")).thenReturn(true);

        MessageResponse res = service.sendMessage("c-1", "u-supplier", MessageSendRequest.builder().messageText("Hi").build());

        assertThat(res.isRead()).isTrue();
        assertThat(conversation.getUnreadCountBuyer()).isZero();
        verify(notificationService, never()).notifyOnce(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void messageToSomeoneAwayCountsAsUnreadAndAlerts() {
        MessageResponse res = service.sendMessage("c-1", "u-supplier", MessageSendRequest.builder().messageText("Hi").build());

        assertThat(res.isRead()).isFalse();
        assertThat(conversation.getUnreadCountBuyer()).isEqualTo(1);
        verify(notificationService).notifyOnce(eq(buyer), eq(NotificationType.MESSAGE), anyString(), eq("Hi"), eq("c-1"));
    }

    @Test
    void openingTheChatClearsItsUnreadCountAndMessageAlert() {
        conversation.setUnreadCountBuyer(3);
        service.markAsRead("c-1", "u-buyer");

        assertThat(conversation.getUnreadCountBuyer()).isZero();
        verify(messageRepository).markReadFor("c-1", "u-buyer");
        verify(notificationService).markReadByReference("u-buyer", NotificationType.MESSAGE, "c-1");
    }

    @Test
    void conversationShowsWhetherTheOtherSideIsOnline() {
        when(manufacturerRepository.findByUserId("u-buyer")).thenReturn(Optional.empty());
        when(conversationRepository.findByBuyerIdOrderByLastMessageAtDesc(eq("u-buyer"), any(Pageable.class)))
                .thenReturn(List.of(conversation));
        when(presenceService.isOnline("u-supplier")).thenReturn(true);

        List<ConversationResponse> list = service.listRecent("u-buyer", 20);

        assertThat(list.get(0).isCounterpartOnline()).isTrue();
    }

    @Test
    void unreadCountSumsTheViewersSideOfEveryChat() {
        conversation.setUnreadCountBuyer(2);
        conversation.setUnreadCountSupplier(5);
        when(manufacturerRepository.findByUserId("u-buyer")).thenReturn(Optional.empty());
        when(conversationRepository.sumUnreadForBuyer("u-buyer")).thenReturn(2L);

        assertThat(service.unreadCount("u-buyer")).isEqualTo(2);
    }
}
