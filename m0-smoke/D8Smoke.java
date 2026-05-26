import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class D8Smoke {
    public static void main(String[] args) throws Exception {
        byte[] classBytes = Files.readAllBytes(Paths.get("GenHello.class"));
        System.out.println("input .class size = " + classBytes.length);

        byte[][] dexOut = new byte[1][];

        D8Command.Builder b = D8Command.builder();
        b.addClassProgramData(classBytes, Origin.unknown());
        b.setMinApiLevel(26);
        b.setProgramConsumer(new DexIndexedConsumer.ForwardingConsumer(null) {
            @Override
            public void accept(int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
                dexOut[0] = data.copyByteData();
            }
        });

        D8.run(b.build());

        if (dexOut[0] == null) throw new AssertionError("no dex produced");
        Files.write(Paths.get("classes.dex"), dexOut[0]);
        System.out.println("output .dex size = " + dexOut[0].length);

        byte[] header = new byte[8];
        System.arraycopy(dexOut[0], 0, header, 0, 8);
        StringBuilder sb = new StringBuilder("magic = ");
        for (int i = 0; i < 8; i++) sb.append(String.format("%02x ", header[i] & 0xff));
        System.out.println(sb);
        if (!(header[0] == 'd' && header[1] == 'e' && header[2] == 'x' && header[3] == '\n'))
            throw new AssertionError("bad dex magic");

        System.out.println("D8 smoke PASS");
    }
}
