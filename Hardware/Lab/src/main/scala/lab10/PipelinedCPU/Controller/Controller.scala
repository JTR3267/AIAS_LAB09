package lab10.PiplinedCPU.Controller

import chisel3._
import chisel3.util._

import lab10.PiplinedCPU.opcode_map._
import lab10.PiplinedCPU.condition._
import lab10.PiplinedCPU.inst_type._
import lab10.PiplinedCPU.alu_op_map._
import lab10.PiplinedCPU.pc_sel_map._
import lab10.PiplinedCPU.wb_sel_map._
import lab10.PiplinedCPU.forwarding_sel_map._

class Controller(memAddrWidth: Int) extends Module {
  val io = IO(new Bundle {
    // Memory control signal interface
    val IM_Mem_R = Output(Bool()) 
    val IM_Mem_W = Output(Bool()) 
    val IM_Length = Output(UInt(4.W))
    val IM_Valid = Input(Bool()) 

    val DM_Mem_R = Output(Bool()) 
    val DM_Mem_W = Output(Bool()) 
    val DM_Length = Output(UInt(4.W))
    val DM_Valid = Input(Bool()) 

    // branch Comp.
    val E_BrEq = Input(Bool())
    val E_BrLT = Input(Bool())

    // Branch Prediction
    val BP_taken = Input(Bool())
    val E_Branch_taken = Output(Bool())
    val E_En = Output(Bool())

    val ID_pc = Input(UInt(memAddrWidth.W))
    val EXE_BP_taken = Input(Bool())
    val EXE_target_pc = Input(UInt(memAddrWidth.W))

    // Flush
    val Flush = Output(Bool()) //TBD

    // Stall
    // To Be Modified
    val Stall_DH = Output(Bool()) //TBD
    val Stall_MA = Output(Bool()) //TBD

    // inst
    val IF_Inst = Input(UInt(32.W))
    val ID_Inst = Input(UInt(32.W))
    val EXE_Inst = Input(UInt(32.W))
    val MEM_Inst = Input(UInt(32.W))
    val WB_Inst = Input(UInt(32.W))

    // sel
    val PCSel = Output(UInt(2.W))
    val D_ImmSel = Output(UInt(3.W))
    val W_RegWEn = Output(Bool())
    val E_BrUn = Output(Bool())
    val E_ASel = Output(UInt(2.W))
    val E_BSel = Output(UInt(1.W))
    val E_ALUSel = Output(UInt(15.W))
    val W_WBSel = Output(UInt(2.W))

    val Hcf = Output(Bool())
  })
  // Inst Decode for each stage 
  val IF_opcode = io.IF_Inst(6, 0)
  val IF_rs1 = io.IF_Inst(19,15)     //  lab10_2
  val IF_rs2 = io.IF_Inst(24,20)     //  lab10_2

  val ID_opcode = io.ID_Inst(6, 0)

  val EXE_opcode = io.EXE_Inst(6, 0)
  val EXE_funct3 = io.EXE_Inst(14, 12)
  val EXE_funct7 = io.EXE_Inst(31, 25)

  val MEM_opcode = io.MEM_Inst(6, 0)
  val MEM_funct3 = io.MEM_Inst(14, 12)
  val MEM_rd = io.MEM_Inst(11, 7)      //  lab10_2

  val WB_opcode = io.WB_Inst(6, 0)


  // Control signal - Branch/Jump
  val E_En = Wire(Bool())
  E_En := (EXE_opcode===BRANCH)         // To Be Modified
  val E_Branch_taken = Wire(Bool())
  E_Branch_taken := MuxLookup(EXE_opcode, false.B, Seq(
          BRANCH -> MuxLookup(EXE_funct3, false.B, Seq(
            "b000".U(3.W) -> io.E_BrEq.asUInt,
          )),
        ))    // To Be Modified
        
  io.E_En := E_En
  io.E_Branch_taken := E_Branch_taken

  // pc predict miss signal
  val Predict_Miss = Wire(Bool())
  Predict_Miss := (E_En && ((io.EXE_BP_taken =/= E_Branch_taken) || (E_Branch_taken && io.ID_pc=/=io.EXE_target_pc)))
  
  // Control signal - Branch Prediction (Lab12)
  val BP_En = Wire(Bool()) // Branch Predict Enable
  BP_En := (IF_opcode===BRANCH)    // To Be Modified

  // Control signal - PC
  when(Predict_Miss){
    io.PCSel := EXE_T_PC
  }.otherwise{
    io.PCSel := IF_PC_PLUS_4
  }   // (To Be Modified in Lab12)

  // Control signal - Branch comparator
  io.E_BrUn := (io.EXE_Inst(13) === 1.U)

  // Control signal - Immediate generator
  io.D_ImmSel := MuxLookup(ID_opcode, 0.U, Seq(
    OP_IMM -> I_type,
    LOAD -> I_type,
    BRANCH -> B_type,
    LUI -> U_type,
  )) // To Be Modified

  // Control signal - Scalar ALU
  io.E_ASel := MuxLookup(EXE_opcode, 0.U, Seq(
    BRANCH -> 1.U,
    LUI -> 2.U,
  ))    // To Be Modified
  io.E_BSel := 1.U // To Be Modified
  
  io.E_ALUSel := MuxLookup(EXE_opcode, (Cat(0.U(7.W), "b11111".U, 0.U(3.W))), Seq(
    OP -> (Cat(EXE_funct7, "b11111".U, EXE_funct3)),
    OP_IMM -> (Cat(0.U(7.W), "b11111".U, EXE_funct3))
  )) // To Be Modified

  // Control signal - Data Memory
  io.DM_Mem_R := (MEM_opcode===LOAD)
  io.DM_Mem_W := (MEM_opcode===STORE)
  io.DM_Length := Cat(0.U(1.W),MEM_funct3) // length

  // Control signal - Inst Memory
  io.IM_Mem_R := true.B // always true
  io.IM_Mem_W := false.B // always false
  io.IM_Length := "b0010".U // always load a word(inst)

  // Control signal - Scalar Write Back
  io.W_RegWEn := MuxLookup(WB_opcode, false.B, Seq(
    OP_IMM -> true.B,
    LOAD -> true.B,
    LUI -> true.B,
  ))  // To Be Modified

  
  io.W_WBSel := MuxLookup(WB_opcode, ALUOUT, Seq(
    LOAD -> LD_DATA,
  )) // To Be Modified

  // Control signal - Others
  io.Hcf := (IF_opcode === HCF)

  /****************** Data Hazard ******************/
  // Use rs in IF stage 
  val is_F_use_rs1 = Wire(Bool()) 
  val is_F_use_rs2 = Wire(Bool())
  is_F_use_rs1 := MuxLookup(IF_opcode,false.B,Seq(
    BRANCH -> true.B,
  ))   // To Be Modified
  is_F_use_rs2 := MuxLookup(IF_opcode,false.B,Seq(
    BRANCH -> true.B,
  ))   // To Be Modified

  // Use rd in ID stage 

  // Use rd in EXE stage

  // Use rd in MEM stage
  val is_M_use_rd = Wire(Bool())
  is_M_use_rd := MuxLookup(MEM_opcode,false.B,Seq(
    OP_IMM -> true.B,
  ))   // To Be Modified

  // Hazard condition (rd, rs overlap)
  val is_F_rs1_M_rd_overlap = Wire(Bool())
  val is_F_rs2_M_rd_overlap = Wire(Bool())

  is_F_rs1_M_rd_overlap := is_F_use_rs1 && is_M_use_rd && (IF_rs1 === MEM_rd) && (MEM_rd =/= 0.U(5.W))
  is_F_rs2_M_rd_overlap := is_F_use_rs2 && is_M_use_rd && (IF_rs2 === MEM_rd) && (MEM_rd =/= 0.U(5.W))
 
  // WB Hazard (rs2, rs1) - to stage Reg

  // Control signal - Stall
  // Stall for Data Hazard
  io.Stall_DH := (is_F_rs1_M_rd_overlap || is_F_rs2_M_rd_overlap)
  io.Stall_MA := false.B // Stall for Waiting Memory Access
  // Control signal - Flush
  io.Flush := Predict_Miss

  // Control signal - Data Forwarding (Bonus)

  /****************** Data Hazard End******************/
}
