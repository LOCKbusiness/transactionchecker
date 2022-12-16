package ch.dfx.transactionserver.updater;

import static ch.dfx.transactionserver.database.DatabaseUtils.TOKEN_NETWORK_CUSTOM_SCHEMA;
import static ch.dfx.transactionserver.database.DatabaseUtils.TOKEN_PUBLIC_SCHEMA;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.dfx.common.TransactionCheckerUtils;
import ch.dfx.common.enumeration.NetworkEnum;
import ch.dfx.common.errorhandling.DfxException;
import ch.dfx.defichain.data.transaction.DefiTransactionData;
import ch.dfx.defichain.data.transaction.DefiTransactionScriptPubKeyData;
import ch.dfx.defichain.data.transaction.DefiTransactionVoutData;
import ch.dfx.defichain.provider.DefiDataProvider;
import ch.dfx.transactionserver.builder.DatabaseCustomTransactionBuilder;
import ch.dfx.transactionserver.data.AddressDTO;
import ch.dfx.transactionserver.data.BlockDTO;
import ch.dfx.transactionserver.data.BlockTransactionDTO;
import ch.dfx.transactionserver.data.TransactionDTO;
import ch.dfx.transactionserver.database.DatabaseUtils;
import ch.dfx.transactionserver.database.H2DBManager;
import ch.dfx.transactionserver.database.helper.DatabaseBlockHelper;
import ch.dfx.transactionserver.handler.DatabaseAddressHandler;

/**
 * 
 */
public class CustomTransactionUpdater {
  private static final Logger LOGGER = LogManager.getLogger(CustomTransactionUpdater.class);

  // ...
  private static final String CUSTOM_TYPE_NONE = "0";
  private static final String CUSTOM_TYPE_ANY_ACCOUNTS_TO_ACCOUNTS = "a";
  private static final String CUSTOM_TYPE_ACCOUNT_TO_ACCOUNT = "B";

  // ...
  private PreparedStatement transactionSelectStatement = null;
  private PreparedStatement transactionUpdateStatement = null;

  private PreparedStatement missingCustomTransactionSelectStatement = null;

  // ...
  private final NetworkEnum network;

  private final H2DBManager databaseManager;
  private final DatabaseBlockHelper databaseBlockHelper;

  private final DatabaseAddressHandler databaseAddressHandler;
  private final DatabaseCustomTransactionBuilder customTransactionBuilder;

  private final DefiDataProvider dataProvider;

  // ...
  private Integer lastBlockNumber = -1;
  private String lastBlockHash = "";

  /**
   * 
   */
  public CustomTransactionUpdater(
      @Nonnull NetworkEnum network,
      @Nonnull H2DBManager databaseManager) {
    this.network = network;
    this.databaseManager = databaseManager;

    this.databaseBlockHelper = new DatabaseBlockHelper(network);

    this.dataProvider = TransactionCheckerUtils.createDefiDataProvider();

    this.databaseAddressHandler = new DatabaseAddressHandler(network);
    this.customTransactionBuilder = new DatabaseCustomTransactionBuilder(network, databaseBlockHelper, databaseAddressHandler);
  }

  /**
   * Set "custom_type_code" in TRANSACTION, if null
   */
  public void updateTransaction() throws DfxException {
    LOGGER.trace("updateTransaction()");

    Connection connection = null;

    try {
      connection = databaseManager.openConnection();

      databaseBlockHelper.openStatements(connection);
      openStatements(connection);

      // ...
      int limit = 10000;

      for (int i = 0; i < 5; i++) {
        List<TransactionDTO> transactionDTOList = getTransactionDTOList(limit);

        for (TransactionDTO transactionDTO : transactionDTOList) {
          LOGGER.debug("Block: " + transactionDTO.getBlockNumber() + " / " + transactionDTO.getNumber());

          updateTransactionDTO(transactionDTO);

          if (transactionDTO.isInternalStateChanged()) {
            saveTransactionDTO(transactionDTO);
          }
        }

        connection.commit();
      }

      closeStatements();
      databaseBlockHelper.closeStatements();
    } catch (DfxException e) {
      DatabaseUtils.rollback(connection);
      throw e;
    } catch (Exception e) {
      DatabaseUtils.rollback(connection);
      throw new DfxException("updateTransaction", e);
    } finally {
      databaseManager.closeConnection(connection);
    }
  }

  /**
   * Create and Save Custom Transaction ...
   */
  public void updateCustomTransaction() throws DfxException {
    LOGGER.trace("updateCustomTransaction()");

    updateCustomTransaction(CUSTOM_TYPE_ANY_ACCOUNTS_TO_ACCOUNTS);
    updateCustomTransaction(CUSTOM_TYPE_ACCOUNT_TO_ACCOUNT);
  }

  /**
   * 
   */
  private void updateCustomTransaction(@Nonnull String customTypeCode) throws DfxException {
    LOGGER.trace("updateCustomTransaction()");

    Connection connection = null;

    try {
      connection = databaseManager.openConnection();

      databaseBlockHelper.openStatements(connection);
      openStatements(connection);

      // ...
      customTransactionBuilder.fillCustomTypeCodeToNumberMap(connection);
      databaseAddressHandler.setup(connection);

      // ...
      int limit = 10000;

      for (int i = 0; i < 1; i++) {
        List<BlockTransactionDTO> blockTransactionDTOList = getBlockTransactionDTOList(customTypeCode, limit);

        for (BlockTransactionDTO blockTransactionDTO : blockTransactionDTOList) {
          LOGGER.debug("Block: " + blockTransactionDTO.getBlockNumber() + " / " + blockTransactionDTO.getTransactionNumber());

          TransactionDTO transactionDTO = createCustomTransaction(blockTransactionDTO);

          Map<String, AddressDTO> newAddressMap = databaseAddressHandler.getNewAddressMap();
          databaseBlockHelper.saveAddress(newAddressMap);
          databaseAddressHandler.reset();

          databaseBlockHelper.saveCustomTransaction(transactionDTO);
        }

        connection.commit();
      }

      closeStatements();
      databaseBlockHelper.closeStatements();
    } catch (DfxException e) {
      DatabaseUtils.rollback(connection);
      throw e;
    } catch (Exception e) {
      DatabaseUtils.rollback(connection);
      throw new DfxException("updateCustomTransaction", e);
    } finally {
      databaseManager.closeConnection(connection);
    }
  }

  /**
   * 
   */
  private void openStatements(@Nonnull Connection connection) throws DfxException {
    LOGGER.trace("openStatements()");

    try {
      // Transaction ...
      String transactionSelectSql =
          "SELECT * FROM " + TOKEN_PUBLIC_SCHEMA + ".transaction"
              + " WHERE custom_type_code IS NULL"
              + " ORDER BY block_number, number"
              + " LIMIT ?";
      transactionSelectStatement = connection.prepareStatement(DatabaseUtils.replaceSchema(network, transactionSelectSql));

      String transactionUpdateSql =
          "UPDATE " + TOKEN_PUBLIC_SCHEMA + ".transaction"
              + " SET custom_type_code=?"
              + " WHERE block_number=? AND number=? AND txid=?";
      transactionUpdateStatement = connection.prepareStatement(DatabaseUtils.replaceSchema(network, transactionUpdateSql));

      // Custom Transaction ...
      String missingCustomTransactionSelectSql =
          "SELECT"
              + " b.number AS block_number,"
              + " b.hash AS block_hash,"
              + " t.number AS transaction_number,"
              + " t.txid AS transaction_id"
              + " FROM " + TOKEN_PUBLIC_SCHEMA + ".transaction t"
              + " JOIN " + TOKEN_PUBLIC_SCHEMA + ".block b ON"
              + " b.number = t.block_number"
              + " LEFT OUTER JOIN " + TOKEN_NETWORK_CUSTOM_SCHEMA + ".account_to_account_in ata_in ON"
              + " ata_in.block_number = t.block_number"
              + " AND ata_in.transaction_number = t.number"
              + " WHERE"
              + " t.custom_type_code=?"
              + " AND ata_in.block_number IS NULL"
              + " GROUP BY"
              + " t.block_number,"
              + " t.txid,"
              + " b.hash"
              + " LIMIT ?";
      missingCustomTransactionSelectStatement = connection.prepareStatement(DatabaseUtils.replaceSchema(network, missingCustomTransactionSelectSql));

    } catch (Exception e) {
      throw new DfxException("openStatements", e);
    }
  }

  /**
   * 
   */
  private void closeStatements() throws DfxException {
    LOGGER.trace("closeStatements()");

    try {
      transactionSelectStatement.close();
      transactionUpdateStatement.close();

      missingCustomTransactionSelectStatement.close();
    } catch (Exception e) {
      throw new DfxException("closeStatements", e);
    }
  }

  /**
   * 
   */
  private List<TransactionDTO> getTransactionDTOList(int limit) throws DfxException {
    LOGGER.trace("getTransactionDTOList()");

    try {
      List<TransactionDTO> transactionDTOList = new ArrayList<>();

      transactionSelectStatement.setInt(1, limit);

      ResultSet resultSet = transactionSelectStatement.executeQuery();

      while (resultSet.next()) {
        TransactionDTO transactionDTO =
            new TransactionDTO(
                resultSet.getInt("block_number"),
                resultSet.getInt("number"),
                resultSet.getString("txid"));
        transactionDTO.setCustomTypeCode(resultSet.getString("custom_type_code"));

        transactionDTO.keepInternalState();

        transactionDTOList.add(transactionDTO);
      }

      resultSet.close();

      return transactionDTOList;
    } catch (Exception e) {
      throw new DfxException("getTransactionDTOList", e);
    }
  }

  /**
   * 
   */
  private void updateTransactionDTO(@Nonnull TransactionDTO transactionDTO) throws DfxException {
    LOGGER.trace("updateTransactionDTO()");

    Integer blockNumber = transactionDTO.getBlockNumber();
    String transactionId = transactionDTO.getTransactionId();

    transactionDTO.setCustomTypeCode(CUSTOM_TYPE_NONE);

    // ...
    Boolean isAppliedCustomTransaction =
        dataProvider.isAppliedCustomTransaction(transactionId, (long) blockNumber);

    if (BooleanUtils.isTrue(isAppliedCustomTransaction)) {
      String blockHash = getBlockHash(blockNumber, transactionId);

      DefiTransactionData transactionData = dataProvider.getTransaction(transactionId, blockHash);

      byte customType = getCustomType(transactionData);

      if (0x00 != customType) {
        String typeCode = String.valueOf((char) customType);
        transactionDTO.setCustomTypeCode(typeCode);
      }
    }
  }

  /**
   * Return: 0x00 = No Custom Type
   */
  private byte getCustomType(@Nonnull DefiTransactionData transactionData) throws DfxException {
    byte customType = 0x00;

    for (DefiTransactionVoutData transactionVoutData : transactionData.getVout()) {
      DefiTransactionScriptPubKeyData transactionVoutScriptPubKeyData = transactionVoutData.getScriptPubKey();
      String scriptPubKeyHex = transactionVoutScriptPubKeyData.getHex();

      customType = dataProvider.getCustomType(scriptPubKeyHex);

      if (0x00 != customType) {
        break;
      }
    }

    return customType;
  }

  /**
   * 
   */
  private String getBlockHash(
      @Nonnull Integer blockNumber,
      @Nonnull String transactionId) throws DfxException {
    String blockHash;

    if (blockNumber == lastBlockNumber) {
      blockHash = lastBlockHash;
    } else {
      BlockDTO blockDTO = databaseBlockHelper.getBlockDTOByNumber(blockNumber);

      if (null == blockDTO) {
        throw new DfxException("no block found: block number=" + blockNumber + " / transaction id=" + transactionId);
      }

      blockHash = blockDTO.getHash();

      lastBlockNumber = blockNumber;
      lastBlockHash = blockHash;
    }

    return blockHash;
  }

  /**
   * 
   */
  private void saveTransactionDTO(@Nonnull TransactionDTO transactionDTO) throws DfxException {
    LOGGER.trace("saveTransactionDTO()");

    try {
      transactionUpdateStatement.setString(1, transactionDTO.getCustomTypeCode());

      transactionUpdateStatement.setInt(2, transactionDTO.getBlockNumber());
      transactionUpdateStatement.setInt(3, transactionDTO.getNumber());
      transactionUpdateStatement.setString(4, transactionDTO.getTransactionId());

      transactionUpdateStatement.execute();
    } catch (Exception e) {
      throw new DfxException("saveTransactionDTO", e);
    }
  }

  /**
   * 
   */
  /**
   * 
   */
  private List<BlockTransactionDTO> getBlockTransactionDTOList(@Nonnull String customTypeCode, int limit) throws DfxException {
    LOGGER.trace("getBlockTransactionDTOList()");

    try {
      List<BlockTransactionDTO> blockTransactionDTOList = new ArrayList<>();

      missingCustomTransactionSelectStatement.setString(1, customTypeCode);
      missingCustomTransactionSelectStatement.setInt(2, limit);

      ResultSet resultSet = missingCustomTransactionSelectStatement.executeQuery();

      while (resultSet.next()) {
        BlockTransactionDTO blockTransactionDTO =
            new BlockTransactionDTO(
                resultSet.getInt("block_number"),
                resultSet.getString("block_hash"),
                resultSet.getInt("transaction_number"),
                resultSet.getString("transaction_id"));

        blockTransactionDTOList.add(blockTransactionDTO);
      }

      resultSet.close();

      return blockTransactionDTOList;
    } catch (Exception e) {
      throw new DfxException("getBlockTransactionDTOList", e);
    }
  }

  /**
   * 
   */
  private TransactionDTO createCustomTransaction(@Nonnull BlockTransactionDTO blockTransactionDTO) throws DfxException {

    Integer blockNumber = blockTransactionDTO.getBlockNumber();
    String blockHash = blockTransactionDTO.getBlockHash();
    Integer transactionNumber = blockTransactionDTO.getTransactionNumber();
    String transactionId = blockTransactionDTO.getTransactionId();

    DefiTransactionData transactionData = dataProvider.getTransaction(transactionId, blockHash);

    TransactionDTO transactionDTO = new TransactionDTO(blockNumber, transactionNumber, transactionId);

    customTransactionBuilder.fillCustomTransactionInfo(transactionData, transactionDTO);

    return transactionDTO;
  }
}
