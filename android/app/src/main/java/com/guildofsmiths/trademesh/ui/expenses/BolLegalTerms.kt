package com.guildofsmiths.trademesh.ui.expenses

import com.guildofsmiths.trademesh.data.BolLegalPreferences.Group
import com.guildofsmiths.trademesh.data.BolLegalPreferences.Preset

/**
 * Preset legal / disclaimer text blocks for the BOL footer.
 *
 * Each entry is a plain-language summary with citations — a starting template,
 * not legal advice. Users should have counsel review before relying on them,
 * especially for regulated interstate or cross-border shipments.
 */
object BolLegalTerms {

    data class PresetInfo(
        val preset: Preset,
        val group: Group,
        val shortLabel: String,
        val jurisdiction: String,
        val body: String
    )

    val ALL: List<PresetInfo> = listOf(

        // ══════════════════════════════════════════════════════════════
        // GROUP: US_DOMESTIC
        // ══════════════════════════════════════════════════════════════

        PresetInfo(
            Preset.US_UCC_POMERENE, Group.US_DOMESTIC,
            "US · UCC Art. 7 / Pomerene Act",
            "United States (domestic)",
            """
            |This document is issued under the Uniform Commercial Code Article 7 (Documents of Title)
            |and the Federal Bills of Lading Act (Pomerene Act), 49 U.S.C. §§ 80101–80116. Unless
            |endorsed "to order" or "to bearer," this bill is non-negotiable. Claims for shortage or
            |damage must be made in writing to the issuer within a reasonable time, and in no event
            |later than the periods prescribed by applicable law.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.US_UCC_SALES, Group.US_DOMESTIC,
            "US · UCC Art. 2 (sale of goods)",
            "United States (domestic sale of goods)",
            """
            |Materials listed above are the subject of a contract for the sale of goods under U.C.C.
            |Article 2. By accepting this document or the goods described, the consignee acknowledges
            |receipt in the quantities and descriptions stated. Any nonconformity must be noted in
            |writing at delivery or within a reasonable time thereafter (U.C.C. § 2-607). Where the
            |aggregate price of goods equals or exceeds $500, the parties intend this document to
            |satisfy the writing requirement of U.C.C. § 2-201 (Statute of Frauds).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.US_IRS_BUSINESS_RECORDS, Group.US_DOMESTIC,
            "US · IRS §§ 162, 6001, 274(d)",
            "United States (federal tax recordkeeping)",
            """
            |This document is maintained as a contemporaneous business record under 26 U.S.C. § 6001
            |and Treas. Reg. § 1.6001-1, supporting the ordinary-and-necessary business expense
            |deduction under 26 U.S.C. § 162. Vehicle, travel, and mileage line items are intended to
            |meet the strict-substantiation requirements of 26 U.S.C. § 274(d). Items marked
            |"[AI est.]" are price estimates, not substantiated amounts, and must be reconciled to a
            |receipt, vendor invoice, or other contemporaneous record before being claimed for tax
            |purposes.
            """.trimMargin()
        ),

        // ══════════════════════════════════════════════════════════════
        // GROUP: US_STATES  (alphabetical by state)
        // ══════════════════════════════════════════════════════════════

        // ── Arizona
        PresetInfo(
            Preset.AZ_MECHANICS_LIEN, Group.US_STATES,
            "AZ · Mechanics' Lien (ARS § 33-981)",
            "Arizona",
            """
            |Pursuant to Arizona Revised Statutes §§ 33-981 through 33-1008, the contractor reserves
            |the right to claim a mechanics' and materialmen's lien against the real property improved
            |by the labor and materials itemized above. A preliminary 20-day notice under A.R.S.
            |§ 33-992.01 has been or will be served where required. This document is an itemized
            |statement of the value of the work and materials furnished.
            """.trimMargin()
        ),

        // ── California
        PresetInfo(
            Preset.CA_MECHANICS_LIEN, Group.US_STATES,
            "CA · Mechanics Lien (Civ. Code §§ 8400-8494)",
            "California",
            """
            |Pursuant to California Civil Code §§ 8400 through 8494, the claimant reserves the right
            |to record a mechanics lien against the real property improved by the labor, services,
            |equipment, or materials itemized above. A preliminary notice under Civ. Code § 8200 has
            |been or will be served on the owner, direct contractor, and construction lender where
            |required. Any waiver or release shall be effective only if in the statutory form required
            |by §§ 8132, 8134, 8136, or 8138.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.CA_HOME_IMPROVEMENT, Group.US_STATES,
            "CA · Home Improvement (B&P Code §§ 7150-7168)",
            "California (CSLB)",
            """
            |Where this work constitutes a home improvement as defined by California Business &
            |Professions Code §§ 7150 through 7168, this document, together with any signed Home
            |Improvement Contract it references, is intended to satisfy the itemization, change-order,
            |and down-payment disclosure requirements of §§ 7159–7159.5. Down payments are limited to
            |the lesser of $1,000 or 10% of the contract price, and progress payments shall not exceed
            |the value of the work performed.
            """.trimMargin()
        ),

        // ── Colorado
        PresetInfo(
            Preset.CO_MECHANICS_LIEN, Group.US_STATES,
            "CO · Mechanics' Lien (C.R.S. § 38-22)",
            "Colorado",
            """
            |Pursuant to Colorado Revised Statutes § 38-22-101 et seq., the contractor reserves the
            |right to file a lien statement against the real property improved by the labor and
            |materials itemized above. A notice of intent to file a lien under § 38-22-109(3) will
            |be served on the owner at least ten (10) days before recording, where required.
            """.trimMargin()
        ),

        // ── Florida
        PresetInfo(
            Preset.FL_CONSTRUCTION_LIEN, Group.US_STATES,
            "FL · Construction Lien (Ch. 713)",
            "Florida",
            """
            |Pursuant to Florida Statutes Chapter 713, Part I (Construction Lien Law), the contractor
            |reserves the right to record a claim of lien against the real property improved by the
            |labor, services, or materials itemized above. Where required, a Notice to Owner under
            |§ 713.06(2) has been or will be served within forty-five (45) days of first furnishing,
            |and a final Contractor's Final Payment Affidavit will be furnished before receiving final
            |payment as required by § 713.06(3)(d).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.FL_HOME_SOLICITATION, Group.US_STATES,
            "FL · Home Solicitation Sales (§ 501.021)",
            "Florida",
            """
            |Where this transaction falls within the Home Solicitation Sales Act, Fla. Stat. § 501.021
            |et seq., the buyer may be entitled to a three (3) business-day right to cancel. A written
            |notice of that right has been delivered with the agreement this document itemizes.
            """.trimMargin()
        ),

        // ── Georgia
        PresetInfo(
            Preset.GA_MECHANICS_LIEN, Group.US_STATES,
            "GA · Materialmen's Lien (OCGA § 44-14-361)",
            "Georgia",
            """
            |Pursuant to O.C.G.A. § 44-14-361 et seq., the contractor reserves the right to file a
            |special lien against the real property improved by the labor, services, or materials
            |itemized above. A Notice of Commencement, Notice to Contractor, and Claim of Lien will be
            |filed within the statutory deadlines (three months from last furnishing for residential;
            |see § 44-14-361.1 for the strict timing requirements).
            """.trimMargin()
        ),

        // ── Illinois
        PresetInfo(
            Preset.IL_MECHANICS_LIEN, Group.US_STATES,
            "IL · Mechanics Lien (770 ILCS 60)",
            "Illinois",
            """
            |Pursuant to the Illinois Mechanics Lien Act, 770 ILCS 60/0.01 et seq., the contractor
            |reserves the right to claim a lien against the real property improved by the labor and
            |materials itemized above. A Sworn Statement required by § 5, and a Contractor's Affidavit
            |required by § 22 for residential work, will be furnished where applicable.
            """.trimMargin()
        ),

        // ── Maryland
        PresetInfo(
            Preset.MD_MECHANICS_LIEN, Group.US_STATES,
            "MD · Mechanics Lien (RP § 9-101)",
            "Maryland",
            """
            |Pursuant to the Maryland Mechanics' Lien Law, Md. Code Ann., Real Prop. § 9-101 et seq.,
            |the contractor reserves the right to petition for a lien against the real property
            |improved by the labor and materials itemized above. A subcontractor's Notice of Intent to
            |Claim Lien under § 9-104 will be served within 120 days of last furnishing, where
            |required.
            """.trimMargin()
        ),

        // ── Massachusetts
        PresetInfo(
            Preset.MA_MECHANICS_LIEN, Group.US_STATES,
            "MA · Mechanic's Lien (M.G.L. ch. 254)",
            "Massachusetts",
            """
            |Pursuant to Massachusetts General Laws chapter 254, the contractor reserves the right to
            |file a Notice of Contract and Statement of Account against the real property improved by
            |the labor and materials itemized above. Strict compliance with §§ 2, 2A, 4, 8, and 11 is
            |required; this document is intended as the itemized account supporting any such filing.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.MA_HOME_IMPROVEMENT, Group.US_STATES,
            "MA · Home Improvement (M.G.L. ch. 142A)",
            "Massachusetts",
            """
            |Where this work constitutes a home improvement contract as defined by M.G.L. ch. 142A,
            |this document, together with any signed agreement it references, is intended to satisfy
            |the written-contract, payment-schedule, and disclosure requirements of §§ 2, 2A, and 17.
            |Deposits may not exceed one-third of the total contract price or the cost of special-order
            |materials, whichever is greater.
            """.trimMargin()
        ),

        // ── New Jersey
        PresetInfo(
            Preset.NJ_MECHANICS_LIEN, Group.US_STATES,
            "NJ · Construction Lien (N.J.S.A. 2A:44A)",
            "New Jersey",
            """
            |Pursuant to the New Jersey Construction Lien Law, N.J.S.A. 2A:44A-1 et seq., the
            |contractor reserves the right to file a Construction Lien Claim against the real property
            |improved by the labor and materials itemized above. Residential construction is subject
            |to the additional notice and arbitration requirements of N.J.S.A. 2A:44A-21.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.NJ_HOME_IMPROVEMENT, Group.US_STATES,
            "NJ · Home Improvement Contractors' Reg. Act",
            "New Jersey (CFA + HICRA)",
            """
            |Where this work constitutes a home-improvement contract as defined by the New Jersey
            |Consumer Fraud Act, N.J.S.A. 56:8-1 et seq., and the Home Improvement Contractors'
            |Registration Act, N.J.S.A. 56:8-136 et seq., this document together with any signed
            |agreement is intended to satisfy the written-contract, notice, and three-day right to
            |cancel requirements of N.J.A.C. 13:45A-16.1 et seq.
            """.trimMargin()
        ),

        // ── New York
        PresetInfo(
            Preset.NY_LIEN_LAW, Group.US_STATES,
            "NY · Lien Law §§ 3, 10, 38, 38-a",
            "New York (mechanic's lien)",
            """
            |Labor and materials identified on this document were furnished for a permanent
            |improvement to real property. Pursuant to New York Lien Law §§ 3, 10, 38 and 38-a, the
            |contractor reserves the right to file a notice of mechanic's lien for the value of labor
            |performed and materials furnished but unpaid. This document constitutes an itemized
            |statement of the agreed price or value of each line, given in form substantially
            |complying with § 38-a, and is furnished to the owner or lienor upon demand.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.NY_HOME_IMPROVEMENT, Group.US_STATES,
            "NY · GBL Art. 36-A (home improvement)",
            "New York (home improvement contracts)",
            """
            |Where this work constitutes a home improvement as defined by New York General Business
            |Law Article 36-A (§§ 770 et seq.), this document, together with any signed agreement it
            |references, is intended to satisfy the itemization and disclosure requirements of
            |GBL § 771. Escrow treatment of owner payments (where applicable) conforms to § 71-a of
            |the Lien Law and § 771(1)(e) of the GBL.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.NY_STATUTE_OF_FRAUDS, Group.US_STATES,
            "NY · GOL § 5-701 (Statute of Frauds)",
            "New York",
            """
            |To the extent the underlying agreement is one that by its terms is not to be performed
            |within one year or otherwise falls within New York General Obligations Law § 5-701, this
            |document, taken together with any related signed writing, is intended to serve as a
            |sufficient memorandum in writing subscribed by the party to be charged.
            """.trimMargin()
        ),

        // ── North Carolina
        PresetInfo(
            Preset.NC_LIEN_LAW, Group.US_STATES,
            "NC · Lien on Real Property (NCGS § 44A)",
            "North Carolina",
            """
            |Pursuant to North Carolina General Statutes Chapter 44A, Article 2, the contractor
            |reserves the right to claim a lien on real property for the labor and materials itemized
            |above. A Notice to Lien Agent under § 44A-11.1 has been or will be served where required,
            |and any claim of lien will be filed within the 120-day window set by § 44A-12.
            """.trimMargin()
        ),

        // ── Ohio
        PresetInfo(
            Preset.OH_MECHANICS_LIEN, Group.US_STATES,
            "OH · Mechanics' Lien (ORC § 1311)",
            "Ohio",
            """
            |Pursuant to Ohio Revised Code § 1311.01 et seq., the contractor reserves the right to
            |file an affidavit for mechanics' lien against the real property improved by the labor
            |and materials itemized above. A Notice of Furnishing under § 1311.05 will be served
            |within 21 days of first performance where required, and the affidavit will be filed
            |within the statutory 75-day window for residential or 60 days for non-residential work.
            """.trimMargin()
        ),

        // ── Pennsylvania
        PresetInfo(
            Preset.PA_MECHANICS_LIEN, Group.US_STATES,
            "PA · Mechanics' Lien (49 P.S. § 1101)",
            "Pennsylvania",
            """
            |Pursuant to the Pennsylvania Mechanics' Lien Law of 1963, 49 P.S. § 1101 et seq., the
            |contractor reserves the right to file a claim for lien against the real property improved
            |by the labor and materials itemized above. A Preliminary Notice under § 1501.2, where
            |required for residential work or by subcontractors, has been or will be served within 45
            |days of first performance.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.PA_HOME_IMPROVEMENT, Group.US_STATES,
            "PA · Home Improvement Consumer Protection Act",
            "Pennsylvania (HICPA)",
            """
            |Where this work constitutes a home-improvement contract as defined by the Pennsylvania
            |Home Improvement Consumer Protection Act, 73 P.S. § 517.1 et seq., this document,
            |together with any signed agreement it references, is intended to satisfy the
            |written-contract, registration-number, start/completion-date, and three-day right to
            |cancel requirements of §§ 517.7 and 517.9.
            """.trimMargin()
        ),

        // ── Texas
        PresetInfo(
            Preset.TX_MECHANICS_LIEN, Group.US_STATES,
            "TX · Mechanic's Lien (Tex. Prop. Code ch. 53)",
            "Texas",
            """
            |Pursuant to Texas Property Code Chapter 53 and, where applicable, the constitutional
            |lien under Tex. Const. art. XVI § 37, the contractor reserves the right to file an
            |affidavit for mechanic's and materialman's lien against the real property improved by the
            |labor and materials itemized above. All statutory notices required by §§ 53.056, 53.057,
            |and 53.252 will be served within the deadlines prescribed therein.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.TX_RESIDENTIAL_CONSTRUCTION, Group.US_STATES,
            "TX · Residential Construction Contract (ch. 53 Subch. K)",
            "Texas (residential)",
            """
            |Where this work constitutes a residential construction contract as defined by Tex. Prop.
            |Code ch. 53 Subchapter K, this document, together with any signed agreement it
            |references, is intended to satisfy the written-contract, Disclosure Statement
            |(§ 53.255), and List of Subcontractors and Suppliers (§ 53.256) requirements.
            """.trimMargin()
        ),

        // ── Virginia
        PresetInfo(
            Preset.VA_MECHANICS_LIEN, Group.US_STATES,
            "VA · Mechanic's Lien (Va. Code § 43-1)",
            "Virginia",
            """
            |Pursuant to Virginia Code § 43-1 et seq., the contractor reserves the right to file a
            |memorandum of mechanic's lien against the real property improved by the labor and
            |materials itemized above. The memorandum will be filed within the statutory 90-day
            |window after last performance, and preliminary notice under § 43-4.01 will be served
            |where required.
            """.trimMargin()
        ),

        // ── Washington
        PresetInfo(
            Preset.WA_CONSTRUCTION_LIEN, Group.US_STATES,
            "WA · Construction Lien (RCW 60.04)",
            "Washington",
            """
            |Pursuant to RCW 60.04.011 et seq., the contractor reserves the right to file a claim of
            |lien against the real property improved by the labor and materials itemized above. A
            |Notice to Customer, where required by RCW 18.27.114, has been delivered with the
            |agreement, and a Preclaim Notice under RCW 60.04.031 will be served within 60 days of
            |first performance where required.
            """.trimMargin()
        ),

        // ══════════════════════════════════════════════════════════════
        // GROUP: INTL_COMMERCIAL  (non-carriage international & non-US)
        // ══════════════════════════════════════════════════════════════

        PresetInfo(
            Preset.INTL_CISG, Group.INTL_COMMERCIAL,
            "UN · CISG (Vienna Convention, 1980)",
            "United Nations Convention on Contracts for the International Sale of Goods",
            """
            |Where the parties to the underlying sale have their places of business in different
            |Contracting States, this transaction is governed by the United Nations Convention on
            |Contracts for the International Sale of Goods (CISG), Vienna 1980, unless expressly
            |excluded in writing under Article 6. Obligations of the seller, conformity of the goods,
            |and remedies for breach are as provided in Articles 30–52 and 74–77. Notice of lack of
            |conformity must be given within a reasonable time after discovery and in no event later
            |than two (2) years after actual handover (CISG Art. 39(2)).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_UNIDROIT_PRINCIPLES, Group.INTL_COMMERCIAL,
            "UNIDROIT · Principles of Int'l Commercial Contracts",
            "UNIDROIT (International Institute for the Unification of Private Law)",
            """
            |Where the parties have so agreed, or where applicable law permits reference as a
            |gap-filler, this transaction is governed by the UNIDROIT Principles of International
            |Commercial Contracts (current edition). Good faith and fair dealing (Art. 1.7), hardship
            |(Art. 6.2), and right to cure (Art. 7.1.4) apply to the performance and any renegotiation
            |of the obligations memorialised herein.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_INCOTERMS_2020, Group.INTL_COMMERCIAL,
            "ICC · Incoterms 2020",
            "International Chamber of Commerce — Incoterms 2020",
            """
            |Delivery, risk transfer, and allocation of costs between shipper and consignee are
            |governed by the applicable Incoterm® 2020 rule (EXW, FCA, CPT, CIP, DAP, DPU, DDP, FAS,
            |FOB, CFR, or CIF) as stated on this document or the underlying agreement. In the absence
            |of a stated rule, EXW (Ex Works) at the seller's premises is presumed.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_UNCITRAL_ECOMMERCE, Group.INTL_COMMERCIAL,
            "UN · UNCITRAL Model Law on Electronic Commerce (1996)",
            "United Nations Commission on International Trade Law",
            """
            |Where this document is transmitted, signed, or stored electronically, the parties intend
            |to give effect to the UNCITRAL Model Law on Electronic Commerce (1996), Articles 5-15,
            |and any local enactment thereof. An electronic record shall not be denied legal effect,
            |validity, or enforceability solely because it is in electronic form.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_UNCITRAL_MLETR, Group.INTL_COMMERCIAL,
            "UN · UNCITRAL MLETR (Electronic Transferable Records, 2017)",
            "United Nations Commission on International Trade Law",
            """
            |Where this document is issued in electronic form as an electronic transferable record
            |(including an electronic bill of lading), the parties intend to give effect to the
            |UNCITRAL Model Law on Electronic Transferable Records (MLETR, 2017), and any local
            |enactment thereof. The electronic record is intended to satisfy any legal requirement
            |for a transferable document or instrument, and control of the record shall be the
            |functional equivalent of possession (MLETR Art. 11).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_ICC_URC_522, Group.INTL_COMMERCIAL,
            "ICC · URC 522 (Uniform Rules for Collections)",
            "International Chamber of Commerce — URC 522",
            """
            |Where payment is handled through documentary collection, the parties intend this
            |transaction to be subject to the ICC Uniform Rules for Collections (URC 522). Banks
            |handling the collection are not obliged to examine the merits of any document forwarded
            |and act only as agents for the collection of proceeds in accordance with URC 522.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.UK_SALE_OF_GOODS, Group.INTL_COMMERCIAL,
            "UK · Sale of Goods Act 1979",
            "United Kingdom — B2B sale of goods",
            """
            |Where governed by the laws of England and Wales, Scotland, or Northern Ireland, this
            |transaction is subject to the Sale of Goods Act 1979 (as amended). Implied terms as to
            |title, description, satisfactory quality, and fitness for purpose apply pursuant to
            |sections 12-15. Remedies for breach are as provided in Part V A and VI.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.UK_CONSUMER_RIGHTS, Group.INTL_COMMERCIAL,
            "UK · Consumer Rights Act 2015",
            "United Kingdom — B2C sale of goods",
            """
            |Where the consignee is a consumer within the meaning of the UK Consumer Rights Act 2015,
            |nothing in this document or any related agreement purports to exclude or limit the
            |consumer's statutory rights under Part 1 of that Act (satisfactory quality, fitness for
            |purpose, as-described, installation, digital content).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.CA_SALE_OF_GOODS, Group.INTL_COMMERCIAL,
            "Canada · Provincial Sale of Goods Acts",
            "Canada (provincial)",
            """
            |Where this transaction is governed by the laws of a Canadian province, the parties
            |acknowledge the application of the applicable provincial Sale of Goods Act (e.g., SGA
            |[Ontario] R.S.O. 1990, c. S.1; SGA [BC] R.S.B.C. 1996, c. 410). Implied conditions as to
            |title, merchantable quality, and fitness apply. Québec transactions are governed instead
            |by the Civil Code of Québec, Articles 1708 et seq.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.EU_CONSUMER_RIGHTS, Group.INTL_COMMERCIAL,
            "EU · Consumer Rights Directive 2011/83/EU",
            "European Union — B2C sale",
            """
            |Where the consignee is a consumer resident in an EU Member State and the transaction is
            |a distance or off-premises contract within the meaning of Directive 2011/83/EU on
            |consumer rights, the consumer's right of withdrawal (14 days, Art. 9), pre-contractual
            |information, and delivery obligations (Art. 18) apply. Nothing in this document waives
            |those rights.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.EU_LATE_PAYMENT, Group.INTL_COMMERCIAL,
            "EU · Late Payment Directive 2011/7/EU",
            "European Union — B2B late payment",
            """
            |For business-to-business transactions, Directive 2011/7/EU on combating late payment in
            |commercial transactions applies. Payment is due within thirty (30) days from receipt of
            |this document unless otherwise agreed in writing; statutory interest (ECB refinancing
            |rate + 8 percentage points) and a fixed €40 recovery fee accrue automatically on late
            |payment.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.MX_FEDERAL_CONSUMER, Group.INTL_COMMERCIAL,
            "MX · Ley Federal de Protección al Consumidor",
            "Mexico — federal consumer protection",
            """
            |Where the consignee is a consumer resident in Mexico, this transaction is subject to the
            |Ley Federal de Protección al Consumidor (LFPC), DOF 24-XII-1992 as amended. Warranties,
            |right of return for conforming goods, and procedures before PROFECO apply. Nothing in
            |this document purports to waive any right guaranteed by the LFPC.
            """.trimMargin()
        ),

        // ══════════════════════════════════════════════════════════════
        // GROUP: INTERNATIONAL_CARRIAGE
        // ══════════════════════════════════════════════════════════════

        PresetInfo(
            Preset.US_COGSA, Group.INTERNATIONAL_CARRIAGE,
            "US · COGSA (sea)",
            "United States (international sea carriage)",
            """
            |Shipments moving by sea to or from U.S. ports are governed by the Carriage of Goods by
            |Sea Act (COGSA), 46 U.S.C. § 30701 note. The carrier's liability is limited to US$500
            |per package or customary freight unit unless a higher value is declared in writing by
            |the shipper before shipment and inserted in this document. Suit must be brought within
            |one (1) year after delivery or the date when the goods should have been delivered.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_HAGUE_VISBY, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · Hague-Visby Rules",
            "International (sea, Hague 1924 / Visby 1968 / SDR 1979)",
            """
            |Where applicable, this document is subject to the International Convention for the
            |Unification of Certain Rules of Law relating to Bills of Lading (The Hague Rules, 1924)
            |as amended by the Visby Protocol (1968) and the SDR Protocol (1979). Carrier liability
            |is limited to 666.67 SDR per package or 2 SDR per kilogram of gross weight of the goods
            |lost or damaged, whichever is higher. Notice of loss or damage must be given in writing
            |to the carrier at or before removal of the goods, or within three (3) days in case of
            |loss not apparent. Suit must be brought within one (1) year of delivery.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_HAMBURG, Group.INTERNATIONAL_CARRIAGE,
            "UN · Hamburg Rules",
            "United Nations Convention on the Carriage of Goods by Sea (1978)",
            """
            |Where applicable, this document is subject to the United Nations Convention on the
            |Carriage of Goods by Sea (the Hamburg Rules, 1978). Carrier liability is limited to
            |835 SDR per package or 2.5 SDR per kilogram of gross weight, whichever is higher.
            |Notice of loss must be given in writing no later than the working day following
            |delivery; loss not apparent, within fifteen (15) consecutive days after delivery. Suit
            |must be brought within two (2) years.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_ROTTERDAM, Group.INTERNATIONAL_CARRIAGE,
            "UN · Rotterdam Rules",
            "United Nations Convention on Contracts for the International Carriage of Goods Wholly or Partly by Sea (2009)",
            """
            |Where applicable, this document is subject to the United Nations Convention on
            |Contracts for the International Carriage of Goods Wholly or Partly by Sea (the
            |Rotterdam Rules, 2009). Carrier liability is limited to 875 SDR per package or 3 SDR
            |per kilogram of gross weight, whichever is higher. Notice of loss or damage not
            |apparent must be given within seven (7) working days. Judicial or arbitral proceedings
            |must be instituted within two (2) years of delivery.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_CMR, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · CMR Convention (road)",
            "Convention on the Contract for the International Carriage of Goods by Road (Geneva 1956)",
            """
            |Where applicable, carriage is subject to the Convention on the Contract for the
            |International Carriage of Goods by Road (CMR, Geneva 1956). Carrier liability is
            |limited to 8.33 SDR per kilogram of gross weight short. Reservations regarding
            |apparent loss must be made upon delivery; non-apparent loss within seven (7) days.
            |Suit must be brought within one (1) year, or three (3) years in case of willful
            |misconduct.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_MONTREAL_AIR, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · Montreal Convention (air, 1999)",
            "Convention for the Unification of Certain Rules for International Carriage by Air",
            """
            |Where applicable, carriage by air is subject to the Convention for the Unification of
            |Certain Rules for International Carriage by Air (Montreal 1999). For cargo, carrier
            |liability for destruction, loss, damage or delay is limited to 22 SDR per kilogram
            |(Art. 22(3), as amended in 2019), unless a higher value is declared and a supplementary
            |sum paid. Written complaint must be made within fourteen (14) days of receipt for
            |damage, or twenty-one (21) days for delay (Art. 31).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_WARSAW_AIR, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · Warsaw Convention (air, 1929)",
            "Convention for the Unification of Certain Rules Relating to International Carriage by Air",
            """
            |Where the Montreal Convention does not apply, carriage by air may be subject to the
            |Warsaw Convention of 1929, as amended by the Hague Protocol (1955) and the Montreal
            |Protocols. Carrier liability for cargo under the unamended Warsaw regime is limited to
            |250 gold francs per kilogram. This document shall be construed in conformity with the
            |applicable Warsaw-system treaty in force between the States concerned.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_COTIF_CIM_RAIL, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · COTIF/CIM (rail)",
            "Convention concerning International Carriage by Rail, Appendix B (CIM)",
            """
            |Where applicable, carriage by rail is subject to the Convention concerning International
            |Carriage by Rail (COTIF, Vilnius 1999), Appendix B — Uniform Rules concerning the
            |Contract of International Carriage of Goods by Rail (CIM). Carrier liability is limited
            |to 17 SDR per kilogram of gross weight lost or damaged (Art. 30 § 2 CIM). Claims must be
            |made in writing within the statutory period.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.INTL_CMNI_INLAND, Group.INTERNATIONAL_CARRIAGE,
            "INT'L · CMNI (inland waterway, Budapest 2000)",
            "Budapest Convention on the Contract for the Carriage of Goods by Inland Waterway",
            """
            |Where applicable, carriage by inland waterway is subject to the Budapest Convention on
            |the Contract for the Carriage of Goods by Inland Waterway (CMNI, 2000). Carrier
            |liability for loss or damage is limited to the greater of 666.67 SDR per package or 2
            |SDR per kilogram (Art. 20). Notice of apparent damage must be given on delivery; loss
            |not apparent within seven (7) consecutive days.
            """.trimMargin()
        ),

        // ══════════════════════════════════════════════════════════════
        // GROUP: COMMERCIAL_LIEN / AFFIDAVIT TRADITION
        // ══════════════════════════════════════════════════════════════

        PresetInfo(
            Preset.AFFIDAVIT_ADMIRALTY_C6, Group.COMMERCIAL_LIEN,
            "Affidavit · Admiralty Rule C(6)",
            "Supplemental Rules for Administrative and Admiralty Claims, Rule C(6)",
            """
            |This document and any endorsement hereon is given in the nature of Supplemental Rules
            |for Administrative and Admiralty Claims, Rule C(6), of the Federal Rules of Civil
            |Procedure. Nothing herein is a voluntary submission to any foreign jurisdiction or
            |consent to any forum not already competent at law. Where a claim or counter-claim in
            |the nature of admiralty or maritime attachment is asserted against the goods or
            |services itemized above, the issuer reserves all procedural rights available under
            |Rule C(6) and under 28 U.S.C. § 1333 (admiralty and maritime jurisdiction).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.AFFIDAVIT_SELF_EXECUTING, Group.COMMERCIAL_LIEN,
            "Affidavit · Self-Executing Contract",
            "Commercial-lien / affidavit tradition",
            """
            |This document, upon delivery to the consignee, is intended as a self-executing
            |contract for the goods, labor, and services itemized above. Silence is acquiescence,
            |agreement, and dishonour. Failure of the consignee or any party in interest to rebut,
            |point-for-point, any specific line within a commercially reasonable time — and in no
            |event later than ten (10) business days after delivery of this document — constitutes
            |tacit procuration and agreement to the terms and sums stated. All rights are
            |reserved. In re McCowan, 177 C. 93, 170 (1917).
            """.trimMargin()
        ),
        PresetInfo(
            Preset.AFFIDAVIT_NOTICE_AGENT, Group.COMMERCIAL_LIEN,
            "Affidavit · Notice to Agent / Principal",
            "Commercial-lien / affidavit tradition",
            """
            |Notice to Agent is Notice to Principal; Notice to Principal is Notice to Agent.
            |Delivery of this document upon any employee, officer, agent, or authorized
            |representative of the consignee shall be deemed delivery upon the consignee itself
            |for all purposes of notice, including but not limited to notice under the Uniform
            |Commercial Code, the Federal Bills of Lading Act, and any applicable state lien or
            |contract statute.
            """.trimMargin()
        ),
        PresetInfo(
            Preset.AFFIDAVIT_UCC_1_308, Group.COMMERCIAL_LIEN,
            "Affidavit · UCC § 1-308 (without prejudice)",
            "Commercial-lien / reservation of rights",
            """
            |All rights are reserved without prejudice, U.C.C. § 1-308 (formerly § 1-207). Nothing
            |on this document, and no act of the issuer in connection with this document, shall be
            |construed as an acceptance of any liability imposed by unrebutted assumption,
            |presumption, or by operation of any statute, regulation, or rule not expressly and
            |specifically agreed to in a signed writing by the issuer.
            """.trimMargin()
        )
    )

    fun infoFor(preset: Preset): PresetInfo? = ALL.firstOrNull { it.preset == preset }
    fun inGroup(group: Group): List<PresetInfo> = ALL.filter { it.group == group }
    fun textFor(preset: Preset): String = infoFor(preset)?.body ?: ""
    fun headerFor(preset: Preset): String = infoFor(preset)?.shortLabel ?: preset.name
}
