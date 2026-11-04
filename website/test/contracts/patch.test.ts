import { describe, expect, it } from 'vitest';
import { contracts } from '@/api/contracts';

describe('PATCH bodies keep omitted fields omitted', () => {
  it('course update does not inject defaults', () => {
    expect(contracts.sellerCourses.update.body!.parse({ price: 29900 })).toEqual({ price: 29900 });
  });
  it('coupon update does not reset active / limits', () => {
    expect(contracts.sellerMoney.updateCoupon.body!.parse({ value: 10 })).toEqual({ value: 10 });
  });
});
