package ch.dfx.api.enumeration;

import javax.annotation.Nonnull;

/**
 * 
 */
public enum InvalidReasonEnum {
  INVALID_ISSUER_SIGNATURE("Invalid Issure Signature"),
  INVALID_WITHDRAW_SIGNATURE("Invalid Withdraw Signature");

  // ...
  private final String reason;

  /**
   * 
   */
  private InvalidReasonEnum(@Nonnull String reason) {
    this.reason = reason;
  }

  /**
   * 
   */
  public String getReason() {
    return reason;
  }
}
