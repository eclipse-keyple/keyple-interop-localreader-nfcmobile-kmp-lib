module keypleinterop.readerjvm {
    exports org.eclipse.keyple.interop.localreader.nfcmobile.api;
    requires java.smartcardio;

    requires kotlin.stdlib;
    requires kotlinx.serialization.core;
    requires kotlinx.datetime;
    requires kotlinx.coroutines.core;
    requires napier.jvm;
}