package com.ttwishing.library.util;

/**
 * Created by kurt on 10/28/15.
 */
public class ReusableStringBuilderPool extends BaseObjectPool<ReusableStringBuilderPool.ReusableStringBuilder> {

    private static ReusableStringBuilderPool sInstance;

    public ReusableStringBuilderPool() {
        super(10, 20);
    }

    public synchronized static ReusableStringBuilderPool getInstance() {
        if (sInstance == null) {
            sInstance = new ReusableStringBuilderPool();
        }
        return sInstance;
    }

    @Override
    protected ReusableStringBuilder create(BaseObjectPool<ReusableStringBuilder> pool) {
        return new ReusableStringBuilder(50);
    }

    public class ReusableStringBuilder extends Poolable {

        protected StringBuilder builder;

        protected ReusableStringBuilder(int capacity) {
            this.builder = new StringBuilder(capacity);
        }

        @Override
        protected void cleanup() {
            this.builder.setLength(0);
        }

        public ReusableStringBuilder append(String str) {
            this.builder.append(str);
            return this;
        }

        public ReusableStringBuilder append(long l) {
            this.builder.append(l);
            return this;
        }

        public ReusableStringBuilder append(int i) {
            this.builder.append(i);
            return this;
        }

        public ReusableStringBuilder append(char c) {
            this.builder.append(c);
            return this;
        }

        public void release() {
            super.release();
        }

        public int length() {
            return this.builder.length();
        }

        public void setLength(int length) {
            this.builder.setLength(length);
        }

        public String toString() {
            return this.builder.toString();
        }

        public String toStringWithRelease() {
            String str = this.builder.toString();
            super.release();
            return str;
        }


    }
}
