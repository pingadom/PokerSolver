// Independent card-count reference. Uses only the Java standard library, no PokerLab evaluator.
// Input: one line per world: twelve dealt cards (seat order), then the three fixed flop cards.
// Output: first wins, second wins, ties for all five-card boards and then fixed-flop runouts.
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class CheckWeakHandPhysicalCounts {
    static int card(String text) {
        if (text.length()!=2) throw new IllegalArgumentException("Invalid card");
        int r="23456789TJQKA".indexOf(text.charAt(0)),s="cdhs".indexOf(text.charAt(1));
        if(r<0||s<0) throw new IllegalArgumentException("Invalid card");
        return 4*r+s;
    }
    static int straight(int mask) {
        for(int high=12;high>=4;high--) if((mask&(31<<(high-4)))==(31<<(high-4))) return high+2;
        return (mask&0x100f)==0x100f ? 5 : 0;
    }
    static int top(int mask) { return 31-Integer.numberOfLeadingZeros(mask); }
    static int kickers(int mask,int n) {
        int value=0;
        for(int i=0;i<n;i++) {int r=top(mask);value=(value<<4)|(r+2);mask&=~(1<<r);}
        return value;
    }
    static int score(long cards) {
        int ranks=0,pairs=0,trips=0,quad=-1;
        for(int r=0;r<13;r++) {
            int n=Long.bitCount((cards>>>(4*r))&15);
            if(n>0) ranks|=1<<r;
            if(n>=2) pairs|=1<<r;
            if(n>=3) trips|=1<<r;
            if(n==4) quad=r;
        }
        int flush=0;
        for(int suit=0;suit<4;suit++) {
            long suited=cards&(0x1111111111111L<<suit);
            if(Long.bitCount(suited)>=5) {
                for(int r=0;r<13;r++) if((suited&(1L<<(4*r+suit)))!=0) flush|=1<<r;
                int straight=straight(flush);
                if(straight>0) return (8<<20)|(straight<<16);
                break;
            }
        }
        if(quad>=0) return (7<<20)|((quad+2)<<16)|(kickers(ranks&~(1<<quad),1)<<12);
        if(trips!=0) {
            int t=top(trips),p=pairs&~(1<<t);
            if(p!=0) return (6<<20)|((t+2)<<16)|((top(p)+2)<<12);
        }
        if(flush!=0) return (5<<20)|kickers(flush,5);
        int straight=straight(ranks);
        if(straight>0) return (4<<20)|(straight<<16);
        if(trips!=0) {int t=top(trips);return (3<<20)|((t+2)<<16)|(kickers(ranks&~(1<<t),2)<<8);}
        if(Integer.bitCount(pairs)>=2) {
            int hi=top(pairs),lo=top(pairs&~(1<<hi));
            return (2<<20)|((hi+2)<<16)|((lo+2)<<12)|(kickers(ranks&~((1<<hi)|(1<<lo)),1)<<8);
        }
        if(pairs!=0) {int p=top(pairs);return (1<<20)|((p+2)<<16)|(kickers(ranks&~(1<<p),3)<<4);}
        return kickers(ranks,5);
    }
    static void add(long board,long first,long second,long[] counts) {
        int a=score(board|first),b=score(board|second);
        counts[a>b?0:a<b?1:2]++;
    }
    public static void main(String[] args) throws Exception {
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;int worlds=0;
            if(args.length==1 && args[0].equals("ranks")) {
                while((line=input.readLine())!=null) {
                    String[] tokens=line.trim().split("\\s+");
                    if(tokens.length!=7 || ++worlds>4096) throw new IllegalArgumentException("Bounded seven-card control required");
                    long cards=0;
                    for(String token:tokens) {long bit=1L<<card(token);if((cards&bit)!=0) throw new IllegalArgumentException("Duplicate card");cards|=bit;}
                    System.out.println(score(cards));
                }
                return;
            }
            while((line=input.readLine())!=null) {
                String[] tokens=line.trim().split("\\s+");
                if(tokens.length!=15||++worlds>24) throw new IllegalArgumentException("Exactly bounded six-seat worlds required");
                long dead=0,flop=0;
                for(int i=0;i<15;i++) {
                    long bit=1L<<card(tokens[i]);
                    if((dead&bit)!=0||(flop&bit)!=0) throw new IllegalArgumentException("Duplicate or blocked card");
                    if(i<12) dead|=bit;else flop|=bit;
                }
                long first=(1L<<card(tokens[6]))|(1L<<card(tokens[7]));
                long second=(1L<<card(tokens[10]))|(1L<<card(tokens[11]));
                long[] deck=new long[40];int n=0;
                for(int c=0;c<52;c++) if((dead&(1L<<c))==0) deck[n++]=1L<<c;
                long[] all=new long[3],fixed=new long[3];
                for(int a=0;a<36;a++) for(int b=a+1;b<37;b++) for(int c=b+1;c<38;c++)
                    for(int d=c+1;d<39;d++) for(int e=d+1;e<40;e++)
                        add(deck[a]|deck[b]|deck[c]|deck[d]|deck[e],first,second,all);
                for(int a=0;a<39;a++) if((deck[a]&flop)==0)
                    for(int b=a+1;b<40;b++) if((deck[b]&flop)==0) add(flop|deck[a]|deck[b],first,second,fixed);
                if(Arrays.stream(all).sum()!=658008||Arrays.stream(fixed).sum()!=666) throw new IllegalStateException("Board inventory differs");
                System.out.println(all[0]+","+all[1]+","+all[2]+","+fixed[0]+","+fixed[1]+","+fixed[2]);
            }
            if(worlds!=24) throw new IllegalArgumentException("Exactly 24 worlds required");
        }
    }
}
