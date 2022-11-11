package ch.dfx.transactionserver.cleaner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ch.dfx.TestUtils;
import ch.dfx.logging.MessageEventBus;
import ch.dfx.logging.MessageEventCollector;
import ch.dfx.logging.events.MessageEvent;
import ch.dfx.transactionserver.database.H2DBManager;

/**
 * 
 */
public class StakingWithdrawalReservedCleanerTest {
  private static final Logger LOGGER = LogManager.getLogger(StakingWithdrawalReservedCleanerTest.class);

  // ...
  public static boolean isSuiteContext = false;

  // ...
  private static H2DBManager databaseManagerMock = null;
  private static MessageEventCollector messageEventCollector = null;

  // ...
  private static final String CUSTOMER_ADDRESS = "df1qgz2xyzqwnsn5979syu6ng9wxlc4c2ac98m377f";

  /**
   * 
   */
  @BeforeClass
  public static void globalSetup() {
    if (!isSuiteContext) {
      TestUtils.globalSetup("opentransactionmanager", false);
    }

    databaseManagerMock = TestUtils.databaseManagerMock;

    // ...
    messageEventCollector = new MessageEventCollector();
    MessageEventBus.getInstance().register(messageEventCollector);
  }

  /**
   * 
   */
  @AfterClass
  public static void globalCleanup() {
    if (!isSuiteContext) {
      TestUtils.globalCleanup();
    }
  }

  @Before
  public void before() {
    TestUtils.sqlDelete("public.transaction", "txid='abcde'");

    TestUtils.sqlDelete("public.staking_withdrawal_reserved", "customer_address='" + CUSTOMER_ADDRESS + "'");
    TestUtils.sqlDelete("public.staking", "liquidity_address_number=1 AND deposit_address_number=2 AND customer_address_number=4");

    messageEventCollector.getMessageEventList();
  }

  @Test
  public void durationInTimeTest() {
    LOGGER.debug("durationInTimeTest()");

    try {
      String testTime = LocalDateTime.now().minusHours(5).format(TestUtils.SQL_DATETIME_FORMATTER);

      TestUtils.sqlInsert(
          "public.staking_withdrawal_reserved",
          "withdrawal_id, transaction_id, customer_address, vout, create_time",
          "1, 'abcde', '" + CUSTOMER_ADDRESS + "', 1.50000000, '" + testTime + "'");

      StakingWithdrawalReservedCleaner stakingWithdrawalReservedCleaner = new StakingWithdrawalReservedCleaner(databaseManagerMock);
      stakingWithdrawalReservedCleaner.clean();

      List<Map<String, Object>> reservedAfterDataList =
          TestUtils.sqlSelect("public.staking_withdrawal_reserved", "withdrawal_id=1");

      assertEquals("staking_withdrawal_reserved Size", 1, reservedAfterDataList.size());

      Map<String, Object> dataMap = reservedAfterDataList.get(0);
      assertEquals("WITHDRAWAL_ID", Integer.toString(1), dataMap.get("WITHDRAWAL_ID").toString());
      assertEquals("TRANSACTION_ID", "abcde", dataMap.get("TRANSACTION_ID"));
      assertEquals("CUSTOMER_ADDRESS", CUSTOMER_ADDRESS, dataMap.get("CUSTOMER_ADDRESS"));
      assertEquals("VOUT", "1.50000000", dataMap.get("VOUT").toString());

      // ...
      List<MessageEvent> messageEventList = messageEventCollector.getMessageEventList();
      assertEquals("Message Event List Size", 0, messageEventList.size());
    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }

  @Test
  public void durationOvertimeTest() {
    LOGGER.debug("durationOvertimeTest()");

    try {
      String testTime = LocalDateTime.now().minusHours(25).format(TestUtils.SQL_DATETIME_FORMATTER);

      TestUtils.sqlInsert(
          "public.staking_withdrawal_reserved",
          "withdrawal_id, transaction_id, customer_address, vout, create_time",
          "1, 'abcde', '" + CUSTOMER_ADDRESS + "', 0.12345678, '" + testTime + "'");

      StakingWithdrawalReservedCleaner stakingWithdrawalReservedCleaner = new StakingWithdrawalReservedCleaner(databaseManagerMock);
      stakingWithdrawalReservedCleaner.clean();

      List<Map<String, Object>> reservedAfterDataList =
          TestUtils.sqlSelect("public.staking_withdrawal_reserved", "withdrawal_id=1");

      assertEquals("staking_withdrawal_reserved Size", 1, reservedAfterDataList.size());

      Map<String, Object> dataMap = reservedAfterDataList.get(0);
      assertEquals("WITHDRAWAL_ID", Integer.toString(1), dataMap.get("WITHDRAWAL_ID").toString());
      assertEquals("TRANSACTION_ID", "abcde", dataMap.get("TRANSACTION_ID"));
      assertEquals("CUSTOMER_ADDRESS", CUSTOMER_ADDRESS, dataMap.get("CUSTOMER_ADDRESS"));
      assertEquals("VOUT", "0.12345678", dataMap.get("VOUT").toString());

      // ...
      List<MessageEvent> messageEventList = messageEventCollector.getMessageEventList();
      assertEquals("Message Event List Size", 1, messageEventList.size());

      assertEquals(
          "Message",
          "Staking Withdrawal Reserved: 25 hours overtime:"
              + " withdrawalId=1 / transactionId=abcde / vout=0.12345678",
          messageEventList.get(0).getMessage());
    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }

  @Test
  public void transactionInChainTest() {
    LOGGER.debug("transactionInChainTest()");

    try {
      String testTime = LocalDateTime.now().minusHours(100).format(TestUtils.SQL_DATETIME_FORMATTER);

      TestUtils.sqlInsert(
          "public.staking_withdrawal_reserved",
          "withdrawal_id, transaction_id, customer_address, vout, create_time",
          "1, 'abcde', '" + CUSTOMER_ADDRESS + "', 0.12345678, '" + testTime + "'");

      TestUtils.sqlInsert(
          "public.transaction",
          "block_number, number, txid",
          "1, 1, 'abcde'");

      StakingWithdrawalReservedCleaner stakingWithdrawalReservedCleaner = new StakingWithdrawalReservedCleaner(databaseManagerMock);
      stakingWithdrawalReservedCleaner.clean();

      List<Map<String, Object>> reservedAfterDataList =
          TestUtils.sqlSelect("public.staking_withdrawal_reserved", "withdrawal_id=1");

      assertEquals("staking_withdrawal_reserved Size", 0, reservedAfterDataList.size());

      // ...
      List<MessageEvent> messageEventList = messageEventCollector.getMessageEventList();
      assertEquals("Message Event List Size", 0, messageEventList.size());
    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }
}
