package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.exceptions.BadRequestException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanPaymentProofTypeTest {

    @Test
    void acceptsJpegPngWebpAndPdfByContent() {
        assertEquals("image/jpeg", PlanPaymentServiceImpl.sniffContentType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals("image/png", PlanPaymentServiceImpl.sniffContentType(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("image/webp", PlanPaymentServiceImpl.sniffContentType(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertEquals("application/pdf", PlanPaymentServiceImpl.sniffContentType("%PDF-1.7".getBytes()));
    }

    @Test
    void rejectsOtherContentEvenIfNamedLikeAnImage() {
        assertThrows(BadRequestException.class, () -> PlanPaymentServiceImpl.sniffContentType("<html><script>".getBytes()));
        assertThrows(BadRequestException.class, () -> PlanPaymentServiceImpl.sniffContentType(new byte[0]));
    }
}
