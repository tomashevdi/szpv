package ru.tdi.misintegration.szpv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "notification.scheduling.enabled=false")
public class SzpvApplicationTests {

    @Test
    public void contextLoads() {
    }

}
