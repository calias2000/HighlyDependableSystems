package pt.tecnico.bank;

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
import pt.tecnico.bank.grpc.*;

import static io.grpc.Status.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PingHubIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend();
    }

    @AfterEach
    public void tearDown() {
        frontend.close();
        frontend = null;
    }

    @Test
    public void PingHubOKTest() {
        PingRequest request = PingRequest.newBuilder().setInput("localhost:8080").build();
        PingResponse response = frontend.ping(request);
        assertEquals("OK: localhost:8080", response.getOutput());
    }

    @Test
    public void InvalidInputKOTest() {
        PingRequest request = PingRequest.newBuilder().setInput("").build();
        assertEquals(INVALID_ARGUMENT.getCode(), assertThrows(StatusRuntimeException.class, () -> frontend.ping(request)).getStatus().getCode());
    }
}
