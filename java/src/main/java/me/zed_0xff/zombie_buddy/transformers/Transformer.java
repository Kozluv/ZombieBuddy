package me.zed_0xff.zombie_buddy.transformers;

public abstract class Transformer {
    protected static final Result NOOP_RESULT = new Result(null, Resolution.KEEP);

    public enum Resolution {
        KEEP, REPLACE, DELETE, ERROR
    }

    public record Result(byte[] bytes, Resolution resolution) {
        public boolean modified() {
            return resolution != Resolution.KEEP;
        }
    }

    protected ClassContext m_ctx;
    private boolean m_modified;    // means THIS transformer has made changes, not the whole chain as m_ctx.setChanged does
    private Resolution m_resolution = Resolution.KEEP;

    protected void setModified() {
        m_modified = true;
    }

    protected boolean isModified() {
        return m_modified;
    }

    public abstract Result transform(byte[] classBytes, ClassContext ctx);
}
