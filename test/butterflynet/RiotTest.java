package butterflynet;

import com.google.common.io.Resources;
import org.junit.Test;
import riothorn.Riothorn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RiotTest {
    @Test
    public void test() throws IOException {
        Riothorn riothorn = new Riothorn();

        String src = Resources.toString(RiotTest.class.getResource("/butterflynet/tags/capture-list.tag"), StandardCharsets.UTF_8);
        Riothorn.Tag tag = riothorn.compile(src);
        System.out.println(tag.toJavaScript());
        System.out.println(tag.renderJson("{ \"captures\": [{\"url\":\"test\"}]}"));

    }
}
