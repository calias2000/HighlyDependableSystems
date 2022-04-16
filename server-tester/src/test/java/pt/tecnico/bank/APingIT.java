package pt.tecnico.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.grpc.*;

import static org.junit.jupiter.api.Assertions.*;

public class APingIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend(0);
    }

    @AfterEach
    public void tearDown() {
        frontend.close();
        frontend = null;
    }

    @Test
    public void PingTest() {
        PingRequest request = PingRequest.newBuilder().setInput("Ping").build();
        PingResponse response = frontend.ping(request);
        assertEquals("PingPong", response.getOutput());
    }
}
