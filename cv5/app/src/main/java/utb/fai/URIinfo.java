package utb.fai;

import java.net.URI;

public final class URIinfo {

    public final URI uri;
    public final int depth;

    URIinfo(URI uri, int depth) {
        this.uri = uri;
        this.depth = depth;
    }

    @Override
    public String toString() {
        return uri + " (depth " + depth + ")";
    }
}
