using System;
using System.Collections.Generic;

namespace Fm26Moneyball
{
    public enum SellerRole { Key, Rotation, Surplus, Listed }
    public enum WindowTiming { Early, Mid, Late }

    /// <summary>Everything the fee advisor needs. Money is in millions.</summary>
    public sealed class FeeInput
    {
        public double ListedValue { get; set; }
        public int Age { get; set; } = 25;
        public int MonthsLeft { get; set; } = 24;
        public SellerRole Role { get; set; } = SellerRole.Rotation;
        public WindowTiming Timing { get; set; } = WindowTiming.Mid;
        public bool HypedProspect { get; set; }
        public bool SellerNeedsCash { get; set; }
        /// <summary>0 means no release clause.</summary>
        public double ReleaseClause { get; set; }
    }

    public sealed class FeeAdvice
    {
        public double Expected { get; set; }
        public double Open { get; set; }
        public double Ceiling { get; set; }
        /// <summary>True when the release clause is at or below the expected price.</summary>
        public bool PayClause { get; set; }
        /// <summary>Successive bids to try when one is rejected, ending at the ceiling.</summary>
        public double[] RejectionLadder { get; set; }
        /// <summary>Every multiplier that moved the price off the listed value.</summary>
        public List<KeyValuePair<string, double>> Factors { get; set; }
    }

    /// <summary>
    /// Rule-of-thumb transfer pricing. These are community heuristics, not FM's own formula;
    /// the numbers match the web tool in /fm26 so the two stay comparable.
    /// </summary>
    public static class FeeAdvisor
    {
        public static FeeAdvice Advise(FeeInput i)
        {
            if (i == null) throw new ArgumentNullException(nameof(i));
            if (i.ListedValue <= 0) throw new ArgumentOutOfRangeException(nameof(i), "ListedValue must be positive.");

            var factors = new List<KeyValuePair<string, double>>();
            void Add(string name, double x) { if (x != 1) factors.Add(new KeyValuePair<string, double>(name, x)); }

            Add("Contract", ContractFactor(i.MonthsLeft));
            Add("Age", AgeFactor(i.Age));
            Add("Role", RoleFactor(i.Role));
            Add("Timing", TimingFactor(i.Timing));
            Add("Hype", i.HypedProspect ? 1.1 : 1);
            Add("Seller cash", i.SellerNeedsCash ? .9 : 1);

            double product = 1;
            foreach (var f in factors) product *= f.Value;

            double fair = i.ListedValue * product;
            var advice = new FeeAdvice { Expected = fair, Factors = factors };

            if (i.ReleaseClause > 0 && i.ReleaseClause <= fair)
            {
                advice.PayClause = true;
                advice.Open = advice.Expected = advice.Ceiling = i.ReleaseClause;
                advice.RejectionLadder = new[] { i.ReleaseClause };
                return advice;
            }

            double ceiling = fair * 1.25;
            if (i.ReleaseClause > 0) ceiling = Math.Min(ceiling, i.ReleaseClause);
            double open = Math.Min(fair * .75, ceiling);

            var ladder = new List<double>();
            for (double o = open; o < ceiling * .999 && ladder.Count < 5; o *= 1.08) ladder.Add(o);
            ladder.Add(ceiling);

            advice.Open = open;
            advice.Ceiling = ceiling;
            advice.RejectionLadder = ladder.ToArray();
            return advice;
        }

        static double ContractFactor(int months) =>
            months <= 6 ? .55 : months <= 12 ? .75 : months <= 18 ? .9 : months <= 30 ? 1 : months <= 48 ? 1.08 : 1.15;

        static double AgeFactor(int age) =>
            age >= 33 ? .75 : age >= 31 ? .85 : age >= 29 ? .95 : 1;

        static double RoleFactor(SellerRole r)
        {
            switch (r)
            {
                case SellerRole.Key: return 1.25;
                case SellerRole.Surplus: return .8;
                case SellerRole.Listed: return .7;
                default: return 1;
            }
        }

        static double TimingFactor(WindowTiming t) =>
            t == WindowTiming.Early ? 1.08 : t == WindowTiming.Late ? .94 : 1;
    }
}
