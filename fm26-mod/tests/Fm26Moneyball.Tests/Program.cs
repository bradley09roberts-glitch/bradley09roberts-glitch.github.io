using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using HarmonyLib;

namespace Fm26Moneyball.Tests
{
    // Stand-ins for the game's negotiation code.
    public class DummyOffer { public double Fee = 12.5; public int WeeklyWage { get; set; } = 50; public string Club = "Ajax"; public object Ignored = new object(); }

    public class DummyNegotiation
    {
        [MethodImpl(MethodImplOptions.NoInlining)] public int Counter(double fee, string club) => (int)(fee * 2);
        [MethodImpl(MethodImplOptions.NoInlining)] public int Counter(double fee) => (int)fee;
        [MethodImpl(MethodImplOptions.NoInlining)] public void Submit(DummyOffer offer) { }
        [MethodImpl(MethodImplOptions.NoInlining)] public static double Wage(double x) => x * 1.5;
        [MethodImpl(MethodImplOptions.NoInlining)] public DummyOffer Make() => new DummyOffer();
    }

    static class Program
    {
        static int _failed, _passed;

        static void Check(bool ok, string name)
        {
            if (ok) _passed++; else { _failed++; Console.WriteLine("FAIL  " + name); }
        }

        static bool Near(double a, double b) => Math.Abs(a - b) < 1e-9;

        static int Main()
        {
            Fee();
            Wage();
            Dumper();
            ScanTest();
            TraceTest();
            Console.WriteLine($"{_passed} passed, {_failed} failed");
            return _failed == 0 ? 0 : 1;
        }

        static void Fee()
        {
            var a = FeeAdvisor.Advise(new FeeInput { ListedValue = 12, Age = 24, MonthsLeft = 18 });
            Check(Near(a.Expected, 10.8) && Near(a.Open, 8.1) && Near(a.Ceiling, 13.5), "fee: default 12M / 24y / 18 months");
            Check(a.RejectionLadder.Length == 6 && Near(a.RejectionLadder.Last(), a.Ceiling), "fee: ladder ends at the ceiling");
            Check(a.RejectionLadder.Zip(a.RejectionLadder.Skip(1), (x, y) => y > x).All(b => b), "fee: ladder strictly rises");

            var clause = FeeAdvisor.Advise(new FeeInput { ListedValue = 12, Age = 24, MonthsLeft = 18, ReleaseClause = 9 });
            Check(clause.PayClause && Near(clause.Open, 9) && Near(clause.Ceiling, 9), "fee: clause under expected is paid");

            var capped = FeeAdvisor.Advise(new FeeInput { ListedValue = 12, Age = 24, MonthsLeft = 18, ReleaseClause = 12 });
            Check(!capped.PayClause && Near(capped.Ceiling, 12), "fee: clause above expected caps the ceiling");

            var run = FeeAdvisor.Advise(new FeeInput { ListedValue = 10, Age = 26, MonthsLeft = 4 });
            Check(Near(run.Expected, 5.5), "fee: under 6 months left is x0.55");

            var stacked = FeeAdvisor.Advise(new FeeInput { ListedValue = 10, Age = 34, MonthsLeft = 10, Role = SellerRole.Surplus, Timing = WindowTiming.Late, SellerNeedsCash = true });
            Check(Near(stacked.Expected, 10 * .75 * .75 * .8 * .94 * .9) && stacked.Factors.Count == 5, "fee: stacked discounts multiply");

            Check(Throws<ArgumentOutOfRangeException>(() => FeeAdvisor.Advise(new FeeInput { ListedValue = 0 })), "fee: zero value rejected");
        }

        static void Wage()
        {
            var w = WageAdvisor.Advise(new WageInput { SquadAverageK = 35, TopEarnerK = 140, Status = SquadStatus.Important, Age = 24, AgentAskK = 80, TransferFeeM = 12 });
            Check(Near(w.BandLowK, 42) && Near(w.BandHighK, 63) && Near(w.TargetK, 50.4), "wage: band and target");
            Check(Near(w.CeilingK, 69.3) && Near(w.OpenK, 42.84), "wage: ceiling and opening offer");
            Check(w.Verdict == AskVerdict.OverCeiling && w.Years == 4, "wage: ask above ceiling, 4 years at 24");
            Check(Near(w.AgentFeeCapM, 1.2) && Near(w.SigningOnCapM, 50.4 * 52 / 1000), "wage: agent and signing-on caps");

            var capped = WageAdvisor.Advise(new WageInput { SquadAverageK = 35, TopEarnerK = 60, Status = SquadStatus.Star });
            Check(Near(capped.BandHighK, 60) && Near(capped.BandLowK, 51), "wage: star band capped by top earner");

            var free = WageAdvisor.Advise(new WageInput { SquadAverageK = 35, Status = SquadStatus.Backup, Age = 35, YearsOverride = 2 });
            Check(free.Years == 2 && free.SuggestedYears == 1 && Near(free.AgentFeeCapM, free.TargetK * 10 / 1000), "wage: override years, free-transfer agent cap");

            var none = WageAdvisor.Advise(new WageInput { SquadAverageK = 35 });
            Check(none.Verdict == AskVerdict.NoAsk, "wage: no ask");
            Check(Throws<ArgumentOutOfRangeException>(() => WageAdvisor.Advise(new WageInput())), "wage: zero average rejected");
        }

        static void Dumper()
        {
            var s = ObjectDumper.Describe(new DummyOffer());
            Check(s.Contains("Fee=12.5") && s.Contains("WeeklyWage=50") && s.Contains("Club=Ajax") && !s.Contains("Ignored"), "dump: simple members only");
            Check(ObjectDumper.Describe(null) == "null" && ObjectDumper.Describe(7) == "7" && ObjectDumper.Describe(new List<int> { 1, 2 }) == "List`1[Count=2]", "dump: null, primitive, collection");
        }

        static void ScanTest()
        {
            var asm = typeof(DummyNegotiation).Assembly;
            var lines = Discovery.Scan(new[] { asm }, new[] { "negotiat" });
            Check(lines.Any(l => l.StartsWith("TYPE Fm26Moneyball.Tests.DummyNegotiation")), "scan: finds type by name");
            Check(lines.Count(l => l.Contains("METHOD") && l.Contains("Counter")) == 2, "scan: lists both overloads");
            Check(lines.Any(l => l.Contains("METHOD static Double Wage(Double x)")), "scan: static method signature");

            var byMethod = Discovery.Scan(new[] { asm }, new[] { "submit" });
            Check(byMethod.Any(l => l.Contains("METHOD Void Submit(DummyOffer offer)")) && !byMethod.Any(l => l.Contains("Counter")), "scan: method-name match lists only matching methods");
            Check(!Discovery.IsGameAssembly(typeof(string).Assembly) && !Discovery.IsGameAssembly(typeof(Harmony).Assembly), "scan: skips framework and harmony");
        }

        static void TraceTest()
        {
            var log = new List<string>();
            var h = new Harmony("fm26.tests");
            try
            {
                var asms = new[] { typeof(DummyNegotiation).Assembly };
                var t = typeof(DummyNegotiation).FullName;
                var report = Tracer.Apply(h, asms, new[] { t + "::Counter", t + "::Submit", t + "::Wage", t + "::Make", t + "::Nope", "Missing.Type::X", "bad" }, log.Add);
                Check(report.Count(r => r.StartsWith("OK")) == 5, "trace: patches both overloads, void, static and object-returning");
                Check(report.Any(r => r.StartsWith("MISS") && r.Contains("method not found")) && report.Any(r => r.Contains("type not found")) && report.Any(r => r.StartsWith("SKIP")), "trace: reports bad targets");

                var n = new DummyNegotiation();
                int r1 = n.Counter(5.0, "PSV"); int r2 = n.Counter(7.0); n.Submit(new DummyOffer()); double w = DummyNegotiation.Wage(2); n.Make();
                Check(r1 == 10 && r2 == 7 && Near(w, 3), "trace: original behaviour unchanged");
                Check(log.Any(l => l.Contains("Counter(5, PSV)") && l.EndsWith("=>  10")), "trace: value-type result logged with args");
                Check(log.Any(l => l.Contains("Counter(7)") && l.EndsWith("=>  7")), "trace: second overload");
                Check(log.Any(l => l.Contains("Submit(DummyOffer{") && l.Contains("Fee=12.5") && l.Contains("WeeklyWage=50")), "trace: offer argument is expanded");
                Check(log.Any(l => l.Contains("::Wage(2)") && l.EndsWith("=>  3") && !l.Contains("this=")), "trace: static method has no this");
                Check(log.Any(l => l.Contains("::Make()") && l.Contains("=>  DummyOffer{")), "trace: object result expanded");

                // Wage was already called once above, so a cap of 3 leaves room for exactly two more lines.
                Tracer.MaxHitsPerMethod = 3;
                log.Clear();
                for (int i = 0; i < 5; i++) DummyNegotiation.Wage(i);
                Check(log.Count(l => l.Contains("::Wage")) == 2, "trace: per-method hit cap stops logging");
            }
            finally { h.UnpatchSelf(); Tracer.MaxHitsPerMethod = 200; }
        }

        static bool Throws<T>(Action a) where T : Exception
        {
            try { a(); return false; } catch (T) { return true; }
        }
    }
}
