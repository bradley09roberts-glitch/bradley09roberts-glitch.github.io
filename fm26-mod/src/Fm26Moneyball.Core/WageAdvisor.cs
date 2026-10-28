using System;

namespace Fm26Moneyball
{
    public enum SquadStatus { Star, Important, Rotation, Backup, Prospect }
    public enum AskVerdict { NoAsk, OverCeiling, OverTarget, AtOrUnderTarget }

    /// <summary>Weekly wages are in thousands, fees and totals in millions.</summary>
    public sealed class WageInput
    {
        public double SquadAverageK { get; set; }
        public double TopEarnerK { get; set; }
        public SquadStatus Status { get; set; } = SquadStatus.Important;
        public int Age { get; set; } = 25;
        /// <summary>The agent's current weekly ask; 0 means none yet.</summary>
        public double AgentAskK { get; set; }
        /// <summary>0 for a free transfer.</summary>
        public double TransferFeeM { get; set; }
        /// <summary>0 uses the age-based suggestion.</summary>
        public int YearsOverride { get; set; }
    }

    public sealed class WageAdvice
    {
        public double BandLowK { get; set; }
        public double BandHighK { get; set; }
        public double TargetK { get; set; }
        public double OpenK { get; set; }
        public double CeilingK { get; set; }
        public AskVerdict Verdict { get; set; }
        public int SuggestedYears { get; set; }
        public int Years { get; set; }
        public double SigningOnCapM { get; set; }
        public double AgentFeeCapM { get; set; }
        /// <summary>Saving from cutting the weekly wage 10%, over the whole contract.</summary>
        public double SwapSavingM { get; set; }
        /// <summary>The most of that saving worth handing back as a signing-on fee.</summary>
        public double SwapSigningOnM { get; set; }
        public double WagesAtTargetM { get; set; }
        public double AllInM { get; set; }
        public double WorstCaseM { get; set; }
    }

    /// <summary>
    /// Rule-of-thumb wage banding against the club's own structure. Community heuristics, not FM's formula.
    /// </summary>
    public static class WageAdvisor
    {
        public static WageAdvice Advise(WageInput i)
        {
            if (i == null) throw new ArgumentNullException(nameof(i));
            if (i.SquadAverageK <= 0) throw new ArgumentOutOfRangeException(nameof(i), "SquadAverageK must be positive.");

            double loMul, hiMul;
            Band(i.Status, out loMul, out hiMul);

            double hi = i.SquadAverageK * hiMul;
            if (i.TopEarnerK > 0) hi = Math.Min(hi, i.TopEarnerK);
            double lo = i.SquadAverageK * loMul;
            if (lo > hi) lo = hi * .85;

            double target = lo + .4 * (hi - lo);
            double ceiling = hi * 1.1;
            double open = i.AgentAskK > 0 ? Math.Min(i.AgentAskK * .8, target * .85) : target * .85;

            int suggested = i.Age <= 23 ? 5 : i.Age <= 27 ? 4 : i.Age <= 30 ? 3 : i.Age <= 33 ? 2 : 1;
            int years = i.YearsOverride > 0 ? i.YearsOverride : suggested;

            double annual = target * 52 / 1000;
            double signCap = annual;
            double agentCap = i.TransferFeeM > 0 ? i.TransferFeeM * .1 : target * 10 / 1000;
            double swap = target * .1 * 52 * years / 1000;
            double wages = annual * years;

            return new WageAdvice
            {
                BandLowK = lo,
                BandHighK = hi,
                TargetK = target,
                OpenK = open,
                CeilingK = ceiling,
                Verdict = i.AgentAskK <= 0 ? AskVerdict.NoAsk
                        : i.AgentAskK > ceiling ? AskVerdict.OverCeiling
                        : i.AgentAskK > target ? AskVerdict.OverTarget
                        : AskVerdict.AtOrUnderTarget,
                SuggestedYears = suggested,
                Years = years,
                SigningOnCapM = signCap,
                AgentFeeCapM = agentCap,
                SwapSavingM = swap,
                SwapSigningOnM = swap * .6,
                WagesAtTargetM = wages,
                AllInM = wages + i.TransferFeeM,
                WorstCaseM = ceiling * 52 * years / 1000 + i.TransferFeeM + signCap + agentCap
            };
        }

        static void Band(SquadStatus s, out double lo, out double hi)
        {
            switch (s)
            {
                case SquadStatus.Star: lo = 1.8; hi = 3; break;
                case SquadStatus.Important: lo = 1.2; hi = 1.8; break;
                case SquadStatus.Rotation: lo = .7; hi = 1.2; break;
                case SquadStatus.Backup: lo = .4; hi = .7; break;
                default: lo = .15; hi = .4; break;
            }
        }
    }
}
