package ch.dfx.common.enumeration;

/**
 * 
 */
public enum EnvironmentEnum {
  UNKNOWN,
  WINDOWS,
  MACOS;

  @Override
  public String toString() {
    return this.name().toLowerCase();
  }
}
