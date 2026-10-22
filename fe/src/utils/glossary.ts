/** Giải thích thuật ngữ bằng lời thường, dùng chung cho các trang khách hàng */
export const GLOSSARY = {
  dti: 'Tỷ lệ tiền trả nợ hằng tháng trên thu nhập. Càng thấp thì càng dễ trả nợ.',
  maxSafe: 'Số tiền tối đa bạn có thể vay mà vẫn đủ chi tiêu sinh hoạt.',
  refRate: 'Lãi suất để ước tính. Lãi suất thực tế do ngân hàng quyết định.',
  score: 'Điểm cho biết mức phù hợp giữa hồ sơ của bạn và phương án vay. Điểm càng cao càng phù hợp.',
  living: 'Số tiền tối thiểu mỗi tháng để chi tiêu sinh hoạt, hệ thống ước tính theo số người trong gia đình.',
  disposable: 'Số tiền còn lại mỗi tháng sau khi trừ chi phí sinh hoạt và tiền trả các khoản nợ hiện có.',
  debt: 'Tổng số tiền bạn đang phải trả mỗi tháng cho các khoản vay và thẻ tín dụng hiện có.',
  crossSell: 'Sản phẩm đi kèm khoản vay (ví dụ bảo hiểm) có thể giúp giảm lãi suất hoặc tăng điểm, nhưng có thể mất thêm phí.',
  firstPayment: 'Số tiền phải trả trong kỳ đầu tiên. Các kỳ sau có thể khác tùy cách trả nợ.',
  totalInterest: 'Tổng tiền lãi phải trả trong suốt thời gian vay, tính theo lãi suất ước tính.',
  dtiMax: 'Mức tối đa cho phép của tỷ lệ trên. Vượt mức này thì phương án không đạt.',
} as const;
