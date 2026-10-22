import com.leafreader.app.ReaderGesture;

public final class ReaderGestureTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 static ReaderGesture begin(float x,float y){ReaderGesture g=new ReaderGesture();g.down(x,y,360,8,50,1000,500);return g;}
 public static void main(String[] args){
  check(begin(320,200).up(322,202,1100)==ReaderGesture.NEXT,"tap right advances one page");
  check(begin(30,200).up(30,200,1100)==ReaderGesture.PREVIOUS,"tap left returns one page");
  check(begin(180,200).up(180,200,1100)==ReaderGesture.MENU,"only center tap toggles menu");
  check(begin(270,200).up(100,207,1300)==ReaderGesture.NEXT,"swipe left advances");
  check(begin(70,200).up(250,195,1300)==ReaderGesture.PREVIOUS,"swipe right returns");
  check(begin(180,200).up(182,380,1300)==ReaderGesture.NONE,"vertical scrolling never flips");
  check(begin(320,200).up(320,200,1700)==ReaderGesture.NONE,"long press remains available for text selection");
  check(begin(300,200).up(280,200,1200)==ReaderGesture.NONE,"short drag does not accidentally count as tap");
  ReaderGesture g=begin(300,200);g.move(301,260);g.move(300,200);
  check(g.up(300,200,1300)==ReaderGesture.NONE,"scroll returning to its start is not a tap");
  g=begin(300,200);g.cancel();check(g.up(100,200,1300)==ReaderGesture.NONE,"multi-touch or menu-control cancellation prevents flips");
  g=begin(300,200);g.up(100,200,1300);check(g.up(100,200,1350)==ReaderGesture.NONE,"one physical gesture flips at most once");
  check(begin(320,200).up(370,200,1100)==ReaderGesture.PREVIOUS,"edge-reaching horizontal swipe remains supported");
  System.out.println(count+" gesture checks passed.");
 }
}
