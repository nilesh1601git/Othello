package othello.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanScanTest {
  @Test
  void onlyPrivateIpv4AddressesAreSafeToContact() {
    assertTrue(LanScan.isSafeLanAddress("192.168.1.20"));
    assertTrue(LanScan.isSafeLanAddress("10.0.0.8"));
    assertTrue(LanScan.isSafeLanAddress("172.16.4.2"));
    assertTrue(LanScan.isSafeLanAddress("127.0.0.1"));
    assertFalse(LanScan.isSafeLanAddress("8.8.8.8"));
    assertFalse(LanScan.isSafeLanAddress("0.0.0.0"));
    assertFalse(LanScan.isSafeLanAddress("not-a-host.invalid"));
    assertFalse(LanScan.isSafeLanAddress(""));
  }
}
