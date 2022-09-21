package eu.pharmaledger.epi;

public class SymlinkConfig {
    private String originalFile;
    private String symlinkName;

    public String getOriginalFile() {
        return originalFile;
    }

    public void setOriginalFile(String originalFile) {
        this.originalFile = originalFile;
    }

    public String getSymlinkName() {
        return symlinkName;
    }

    public void setSymlinkName(String symlinkName) {
        this.symlinkName = symlinkName;
    }
}
