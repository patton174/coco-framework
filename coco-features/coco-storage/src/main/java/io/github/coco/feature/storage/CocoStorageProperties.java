package io.github.coco.feature.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Coco 对象存储配置。 */
@ConfigurationProperties("coco.storage")
public class CocoStorageProperties {
    private boolean enabled = true;
    private String type = "local";
    private final Local local = new Local();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Local getLocal() { return local; }
    public void validate() {
        if (!"local".equals(type)) throw new IllegalStateException("coco.storage.type must be local");
        if (local.root == null || local.root.isBlank()) throw new IllegalStateException("coco.storage.local.root is required");
        if (local.maxObjectSize < 0 || local.listMaxSize < 1 || local.listMaxSize > 1000) throw new IllegalStateException("invalid coco.storage limits");
    }
    /** 本地文件系统实现配置。 */
    public static class Local {
        private String root;
        private boolean overwrite = true;
        private long maxObjectSize = 1024L * 1024L * 1024L;
        private int listMaxSize = 1000;
        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
        public boolean isOverwrite() { return overwrite; }
        public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
        public long getMaxObjectSize() { return maxObjectSize; }
        public void setMaxObjectSize(long maxObjectSize) { this.maxObjectSize = maxObjectSize; }
        public int getListMaxSize() { return listMaxSize; }
        public void setListMaxSize(int listMaxSize) { this.listMaxSize = listMaxSize; }
    }
}
