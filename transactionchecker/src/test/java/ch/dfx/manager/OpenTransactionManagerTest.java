package ch.dfx.manager;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ch.dfx.api.ApiAccessHandler;
import ch.dfx.api.data.transaction.OpenTransactionDTO;
import ch.dfx.api.data.transaction.OpenTransactionDTOList;
import ch.dfx.api.data.withdrawal.PendingWithdrawalDTO;
import ch.dfx.api.data.withdrawal.PendingWithdrawalDTOList;
import ch.dfx.common.TransactionCheckerUtils;
import ch.dfx.common.errorhandling.DfxException;
import ch.dfx.defichain.data.transaction.DefiTransactionData;
import ch.dfx.defichain.provider.DefiDataProvider;

/**
 * 
 */
public class OpenTransactionManagerTest {

  private static Gson gson = null;

  private static ApiAccessHandler apiAccessHandler = null;
  private static DefiDataProvider dataProvider = null;
  private static OpenTransactionManager transactionManager = null;

  /**
   * 
   */
  @BeforeClass
  public static void globalSetup() throws DfxException {
    // ...
    String network = "testnet";
    String environment = TransactionCheckerUtils.getEnvironment().name().toLowerCase();

    // ...
    System.setProperty("logFilename", "opentransactionmanager-test-" + network + "-" + environment);
    TransactionCheckerUtils.initLog4j("log4j2.xml");

    // ...
    TransactionCheckerUtils.loadConfigProperties(network, environment);

    // ...
    gson = new GsonBuilder().setPrettyPrinting().create();

    apiAccessHandler = mock(ApiAccessHandler.class);
    dataProvider = mock(DefiDataProvider.class);
    transactionManager = new OpenTransactionManager(apiAccessHandler, dataProvider);
  }

  @Test
  public void dataMissingTest() {
    try {
      when(apiAccessHandler.getPendingWithdrawalDTOList()).thenReturn(new PendingWithdrawalDTOList());
      when(apiAccessHandler.getOpenTransactionDTOList()).thenReturn(new OpenTransactionDTOList());

      transactionManager.execute();
    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }

  @Test
  public void dataEmptyTest() {
    try {
      PendingWithdrawalDTOList pendingWithdrawalDTOList = new PendingWithdrawalDTOList();
      pendingWithdrawalDTOList.add(new PendingWithdrawalDTO());

      OpenTransactionDTOList openTransactionDTOList = new OpenTransactionDTOList();
      openTransactionDTOList.add(new OpenTransactionDTO());

      when(apiAccessHandler.getPendingWithdrawalDTOList()).thenReturn(pendingWithdrawalDTOList);
      when(apiAccessHandler.getOpenTransactionDTOList()).thenReturn(openTransactionDTOList);

      transactionManager.execute();
    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }

  @Test
  public void dataValidTest() {
    try {
      ClassLoader classLoader = this.getClass().getClassLoader();

      // ...
      File jsonWithdrawalFile = new File(classLoader.getResource("json/test-1-withdrawal-good.json").getFile());

      PendingWithdrawalDTOList pendingWithdrawalDTOList =
          gson.fromJson(Files.readString(jsonWithdrawalFile.toPath()), PendingWithdrawalDTOList.class);

      when(apiAccessHandler.getPendingWithdrawalDTOList()).thenReturn(pendingWithdrawalDTOList);

      // ...
      File jsonTransactionFile = new File(classLoader.getResource("json/test-1-transaction-good.json").getFile());

      OpenTransactionDTOList openTransactionDTOList =
          gson.fromJson(Files.readString(jsonTransactionFile.toPath()), OpenTransactionDTOList.class);

      when(apiAccessHandler.getOpenTransactionDTOList()).thenReturn(openTransactionDTOList);

      // ...
      File jsonChainDataFile = new File(classLoader.getResource("json/test-1-chaindata-good.json").getFile());

      DefiTransactionData transactionData =
          gson.fromJson(Files.readString(jsonChainDataFile.toPath()), DefiTransactionData.class);

      when(dataProvider.verifyMessage(anyString(), anyString(), anyString())).thenReturn(true);
      when(dataProvider.decodeRawTransaction(anyString())).thenReturn(transactionData);

      // ...
      transactionManager.execute();

      verify(apiAccessHandler).sendOpenTransactionVerified(anyString(), any());

    } catch (Exception e) {
      fail("no exception expected: " + e.getMessage());
    }
  }
}
